package com.replaymod.agent;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
final class LiveFramebuffer {
    static NativeImage capture(Minecraft mc) {
        var target = mc.getMainRenderTarget();
        int width = target.width, height = target.height;
        var device = RenderSystem.getDevice();
        try (var buffer = device.createBuffer(null, GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, width * height * 4)) {
            var encoder = device.createCommandEncoder();
            encoder.copyTextureToBuffer(target.getColorTexture(), buffer, 0, () -> {}, 0);
            try (var view = encoder.mapBuffer(buffer, true, false)) {
                var image = new NativeImage(width, height, false);
                try {
                    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                        image.setPixelABGR(x, height-y-1, 0xff000000 | view.data().getInt((x+y*width)*4));
                    return image;
                } catch (Throwable failure) { image.close(); throw failure; }
            }
        }
    }
}
