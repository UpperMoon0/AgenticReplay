package com.replaymod.replay.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface EntityLivingBaseAccessor {
    @Accessor("fallFlyTicks") int exportFlightTicks();
    @Accessor("fallFlyTicks") void exportFlightTicks(int value);
    //#if MC>=11400 && MC<12105
    //$$ @Accessor("serverX")
    //$$ double getInterpTargetX();
    //$$ @Accessor("serverY")
    //$$ double getInterpTargetY();
    //$$ @Accessor("serverZ")
    //$$ double getInterpTargetZ();
    //$$ @Accessor("serverYaw")
    //$$ double getInterpTargetYaw();
    //$$ @Accessor("serverPitch")
    //$$ double getInterpTargetPitch();
    //#endif

    //#if MC>=10904
    @Accessor("useItemRemaining")
    int getActiveItemStackUseCount();
    @Accessor("useItemRemaining")
    void setActiveItemStackUseCount(int value);
    //#endif
}
