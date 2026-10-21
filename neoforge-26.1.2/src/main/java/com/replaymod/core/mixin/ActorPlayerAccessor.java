package com.replaymod.core.mixin;

import net.minecraft.world.entity.Avatar;
import net.minecraft.network.syncher.EntityDataAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Avatar.class)
public interface ActorPlayerAccessor {
    @Accessor("DATA_PLAYER_MODE_CUSTOMISATION")
    static EntityDataAccessor<Byte> actorModelParts() { throw new AssertionError("Mixin not applied"); }
}
