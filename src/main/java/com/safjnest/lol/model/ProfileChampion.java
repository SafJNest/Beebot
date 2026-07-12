package com.safjnest.lol.model;

/** Presentation-neutral champion metadata resolved by the profile domain service. */
public record ProfileChampion(
    String name,
    String image
) {}
