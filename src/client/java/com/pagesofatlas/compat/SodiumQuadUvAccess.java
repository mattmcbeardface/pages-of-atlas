package com.pagesofatlas.compat;

/** Direct, allocation-free access to Sodium's final quad UVs. */
public interface SodiumQuadUvAccess {

    float pagesofatlas$getTexU(int vertex);

    float pagesofatlas$getTexV(int vertex);
}
