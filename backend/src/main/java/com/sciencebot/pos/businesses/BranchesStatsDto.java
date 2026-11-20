package com.sciencebot.pos.businesses;

import java.time.LocalDate;
import java.util.List;

/** The same figures per branch, ranked by net amount (highest first). */
public record BranchesStatsDto(LocalDate from, LocalDate to, List<BranchStatsDto> branches) {}
