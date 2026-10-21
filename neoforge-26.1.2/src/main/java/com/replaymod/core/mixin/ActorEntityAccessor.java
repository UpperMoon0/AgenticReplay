package com.replaymod.core.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface ActorEntityAccessor {
    @Invoker("setSharedFlag")
    void actorSetFlag(int index, boolean value);
}
