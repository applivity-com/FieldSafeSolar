package com.example.fieldsafesolar.data.model

data class EewpRecord(
    val taskDescription: String,
    val authorizingSupervisor: String,
    val incidentEnergyCal: String,
    val ppeCategory: String,
    val workingDistanceFt: String,
) {
    fun toDisplayLines(): List<String> = listOf(
        "EEWP — Task: $taskDescription",
        "EEWP — Authorized by: $authorizingSupervisor",
        "EEWP — Incident energy: $incidentEnergyCal cal/cm²  |  PPE Category: $ppeCategory",
        "EEWP — Working distance: $workingDistanceFt ft  |  Standard: NFPA 70E §130.2(B)",
    )
}
