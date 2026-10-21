package com.replaymod.core.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.data.TrackedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerEntity.class)
public interface ActorPlayerAccessor {
    @Accessor("PLAYER_MODEL_PARTS")
    static TrackedData<Byte> actorModelParts() { throw new AssertionError("Mixin not applied"); }
}
