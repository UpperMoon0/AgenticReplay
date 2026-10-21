package com.replaymod.replay;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.replaymod.core.ReplayMod;
import com.replaymod.core.events.PostRenderCallback;
import com.replaymod.render.RenderSettings;
import com.replaymod.render.hooks.EntityRendererHandler;
import de.johni0702.minecraft.gui.versions.Image;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;

public class NoGuiScreenshot {
    private final Image image;
    private final int width;
    private final int height;
    private NoGuiScreenshot(Image image, int width, int height) {
        this.image = image; this.width = width; this.height = height;
    }
    public Image getImage() { return image; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public static ListenableFuture<NoGuiScreenshot> take(Minecraft mc, int width, int height) {
        SettableFuture<NoGuiScreenshot> future = SettableFuture.create();
        ReplayMod.instance.runLater(() -> {
            if (future.isCancelled()) return;
            boolean guiHidden = mc.options.hideGui;
            mc.options.hideGui = true;
            EntityRendererHandler renderer = new EntityRendererHandler(new RenderSettings(), null);
            var events = new de.johni0702.minecraft.gui.utils.EventRegistrations();
            PostRenderCallback callback = new PostRenderCallback() {
                private boolean firstFrame = true;
                @Override public void postRender() {
                    // Capture after the normal update/extract/render sequence. A nested render
                    // can reuse GUI/avatar submissions and GPU uniforms from the outer frame.
                    if (firstFrame) { firstFrame = false; return; }
                    events.unregister();
                    int frameWidth = mc.getMainRenderTarget().width;
                    int frameHeight = mc.getMainRenderTarget().height;
                    try {
                        if (future.isCancelled()) return;
                        Image image;
                        GpuDevice device = RenderSystem.getDevice();
                        try (GpuBuffer gpuBuffer = device.createBuffer(null, GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, frameWidth * frameHeight * 4)) {
                            CommandEncoder cmd = device.createCommandEncoder();
                            cmd.copyTextureToBuffer(mc.getMainRenderTarget().getColorTexture(), gpuBuffer, 0, () -> {}, 0);
                            try (GpuBuffer.MappedView readView = cmd.mapBuffer(gpuBuffer, true, false)) {
                                NativeImage nativeImage = new NativeImage(frameWidth, frameHeight, false);
                                for (int y = 0; y < frameHeight; ++y) {
                                    for (int x = 0; x < frameWidth; ++x) {
                                        int color = readView.data().getInt((x + y * frameWidth) * 4);
                                        nativeImage.setPixelABGR(x, frameHeight - y - 1, 0xff000000 | color);
                                    }
                                }
                                image = new Image(nativeImage);
                            }
                        }
                        int imageWidth = image.getWidth();
                        int imageHeight = image.getHeight();

                        // Scale & crop
                        float scaleFactor = Math.max((float) width / imageWidth, (float) height / imageHeight);
                        int croppedWidth = Math.min(Math.max(0, (int) (width / scaleFactor)), imageWidth);
                        int croppedHeight = Math.min(Math.max(0, (int) (height / scaleFactor)), imageHeight);
                        int offsetX = (imageWidth - croppedWidth) / 2;
                        int offsetY = (imageHeight - croppedHeight) / 2;
                        image = image.scaledSubRect(offsetX, offsetY, croppedWidth, croppedHeight, width, height);

                        // Finish
                        future.set(new NoGuiScreenshot(image, width, height));
                    } catch (Throwable t) {
                        future.setException(t);
                    } finally {
                        mc.options.hideGui = guiHidden;
                        try { renderer.close(); } catch (java.io.IOException e) { future.setException(e); }
                    }
                }
            };
            events.on(PostRenderCallback.EVENT, callback);
            events.register();
        });
        return future;
    }
}
