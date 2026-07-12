package com.safjnest.lol.model;

/** Database row metadata plus the Base64 representation of its Kryo BLOB. */
public record ProfileStatisticsRow(
    long timeStart,
    long timeEnd,
    String data
) {}
