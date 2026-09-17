package com.pagesofatlas.mixin;

import com.pagesofatlas.compat.SodiumQuadTagAccess;
import com.pagesofatlas.compat.SodiumQuadUvAccess;

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
    implements SodiumQuadTagAccess, SodiumQuadUvAccess {

    @Shadow
    public abstract int getTag();

    @Shadow
    public abstract float getTexU(int vertex);

    @Shadow
    public abstract float getTexV(int vertex);

    @Override
    public int pagesofatlas$getSodiumTag() {
        return getTag();
    }

    @Override
    public float pagesofatlas$getTexU(int vertex) {
        return getTexU(vertex);
    }

    @Override
    public float pagesofatlas$getTexV(int vertex) {
        return getTexV(vertex);
    }
}
