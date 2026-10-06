package dev.drimoz.materialnexus.core.resolution;

import dev.drimoz.materialnexus.core.domain.FormId;
import dev.drimoz.materialnexus.core.domain.MaterialId;

import java.util.SortedMap;

public record ResolvedMaterial(MaterialId material, SortedMap<FormId, ResolvedForm> forms) { }
