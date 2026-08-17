package com.pagesofatlas.mixin;

import com.pagesofatlas.compat.SodiumQuadTagAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(
    targets =
        "net.caffeinemc.mods.sodium.client.render.model.QuadViewImpl",
    remap = false
)
public abstract class SodiumQuadViewImplMixin
    implements SodiumQuadTagAccess {

    @Shadow
    public abstract int getTag();

    @Override
    public int pagesofatlas$getSodiumTag() {
        return getTag();
    }
}
