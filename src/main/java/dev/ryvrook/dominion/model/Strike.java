package dev.ryvrook.dominion.model;

import java.util.UUID;

/** An admin penalty against a faction. Number is 1 based and stable until removed. */
public record Strike(int number, int points, String reason, UUID by, String byName, long at) {}
