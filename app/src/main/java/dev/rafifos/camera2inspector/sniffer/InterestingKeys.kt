package dev.rafifos.camera2inspector.sniffer

/**
 * Groups used only to highlight keys during analysis. Matching is textual and does not assign
 * meaning to any value.
 */
object InterestingKeys {

    data class Group(val label: String, val tokens: List<String>)

    val GROUPS: List<Group> = listOf(
        Group(
            "HDR",
            listOf(
                "HDRMode", "SnapshotHDRMode", "EnableAutoHDR", "EnableHDRDCGMode",
                "HDRModePreference", "numHDRexposure", "HDRModes", "HDRDCGModes", "hdr_triggered",
            ),
        ),
        Group(
            "RAW",
            listOf("EnableIdealRAW", "RawCbSourceType", "raw_pt_capture", "raw_pt_support", "support_tf_raw_sr"),
        ),
        Group("MFNR", listOf("enableMFNR", "isMfnrEnabled")),
        Group(
            "Sensor",
            listOf(
                "current_mode", "raw_nv_scene_status", "master_sensor_name", "analog_gain",
                "iso100_gain", "lux_idx", "lux_level",
            ),
        ),
        Group(
            "Zoom",
            listOf(
                "EnableInsensorZoom", "EnableSnapshotOnlyInsensorZoom", "super_zoom",
                "support_in_sensor_crop", "snapshot_crop_region", "active_array_region",
                "residual_zoom", "zoom",
            ),
        ),
        Group(
            "Processing",
            listOf(
                "process_in_app", "offline_postproc", "capture_zsl_mode", "EnableOfflineHALZSL",
                "EnableVSR", "EnableVIULL", "EnableMCXMasterCb", "enableMCTFwithReferenceFrame",
            ),
        ),
        Group(
            "OIS",
            listOf(
                "vstab_ihc_enable", "vstab_smarteis_enable", "vstab_pzs_enable", "EISMode",
                "ois_bokeh", "OIS",
            ),
        ),
        Group(
            "AI/Depth/Portrait",
            listOf(
                "AICameraMode", "DepthMode", "EnableVAI", "EnableAICameraHSR",
                "EnableAFFocusMap", "BlurMode", "bokeh",
            ),
        ),
        Group("Capture control", listOf("zsl", "capture", "snapshot", "shutter", "trigger")),
    )

    fun groupsFor(keyName: String): List<String> =
        GROUPS.filter { group -> group.tokens.any { token -> keyName.contains(token, ignoreCase = true) } }
            .map { it.label }

    fun isInteresting(keyName: String): Boolean = groupsFor(keyName).isNotEmpty()
}
