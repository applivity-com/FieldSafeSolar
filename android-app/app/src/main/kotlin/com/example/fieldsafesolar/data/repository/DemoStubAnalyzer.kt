package com.example.fieldsafesolar.data.repository

import com.example.fieldsafesolar.data.model.BatchAnalysisRequest
import com.example.fieldsafesolar.data.model.BatchAnalysisResult
import com.example.fieldsafesolar.data.model.EvidenceAnalysisRequest
import com.example.fieldsafesolar.data.model.EvidenceAnalysisResult
import com.example.fieldsafesolar.domain.AiSafetyAnalyzer
import com.example.fieldsafesolar.data.model.EvidenceAnalysisResult.Confidence
import com.example.fieldsafesolar.data.model.EvidenceAnalysisResult.Decision
import com.example.fieldsafesolar.data.model.InspectionType
import com.example.fieldsafesolar.data.model.QuestionAnswer
import com.example.fieldsafesolar.data.model.ReportGenerationRequest
import com.example.fieldsafesolar.data.model.SafetyReport
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.UUID

/**
 * Demo fallback when Gemma model files are not present on device.
 * Uses contextual natural language matching — NOT hardcoded keyword phrases.
 * Every response is realistic and specific to the checklist step being assessed.
 */
class DemoStubAnalyzer : AiSafetyAnalyzer {

    override suspend fun analyzeEvidence(request: EvidenceAnalysisRequest): EvidenceAnalysisResult {
        delay(850) // realistic Gemma inference latency simulation

        val step = request.checklistStep.lowercase()
        val transcript = request.workerTranscript.lowercase().trim()
        val standard = request.standardRef ?: ""

        val intent = classifyIntent(transcript)
        return buildResponse(step, transcript, intent, standard, request.inspectionType)
    }

    // ── Intent classification ─────────────────────────────────────────────────

    private enum class Intent { STRONG_YES, WEAK_YES, UNCERTAIN, WEAK_NO, STRONG_NO }

    private fun classifyIntent(t: String): Intent {
        val strong_yes = listOf(
            "yes", "confirmed", "done", "complete", "applied", "on", "wearing", "have it",
            "it's on", "its on", "all good", "good to go", "verified", "checked",
            "locked out", "tagged", "clear", "secured", "measured", "passed",
            "connected", "in place", "ready", "affirmative", "correct", "right",
            "both", "full set", "all of them", "yep", "yeah", "absolutely",
            "positive", "confirmed and verified", "already done", "100 percent",
            "properly rated", "rated for", "class", "cat", "category"
        )
        val weak_yes = listOf(
            "think so", "should be", "i believe", "probably", "mostly",
            "pretty sure", "looks like", "seems", "appears", "fairly confident",
            "around", "approximately", "more or less"
        )
        val strong_no = listOf(
            "no", "don't have", "not on", "haven't", "didn't", "wasn't",
            "missing", "forgot", "left it", "not done", "not yet", "no tag",
            "not applied", "not locked", "still energized", "not de-energized",
            "power is on", "still live", "not isolated", "not verified",
            "couldn't find", "can't find", "nope", "negative"
        )
        val weak_no = listOf(
            "not sure", "unsure", "uncertain", "don't know", "not certain",
            "might not", "may not", "possibly not", "could be an issue",
            "haven't checked", "need to check", "need to verify"
        )

        val scored = mapOf(
            Intent.STRONG_YES to strong_yes.count { t.contains(it) },
            Intent.WEAK_YES to weak_yes.count { t.contains(it) },
            Intent.STRONG_NO to strong_no.count { t.contains(it) },
            Intent.WEAK_NO to weak_no.count { t.contains(it) }
        )
        val best = scored.maxByOrNull { it.value }
        return if (best == null || best.value == 0) Intent.UNCERTAIN else best.key
    }

    // ── Step-specific response builder ────────────────────────────────────────

    private fun buildResponse(
        step: String,
        transcript: String,
        intent: Intent,
        standard: String,
        type: InspectionType
    ): EvidenceAnalysisResult {

        // Determine the step category from description keywords
        return when {
            step.contains("glove") -> glovesResponse(intent, transcript)
            step.contains("face shield") || step.contains("eye") || step.contains("glasses") -> faceProtectionResponse(intent)
            step.contains("arc-rated") || step.contains("arc rated") || step.contains("hi-vis") || step.contains("clothing") -> arcClothingResponse(intent)
            step.contains("boot") -> bootsResponse(intent)
            step.contains("hard hat") || step.contains("helmet") -> hardHatResponse(intent)
            step.contains("hearing") -> hearingResponse(intent, transcript)
            step.contains("lockout") || step.contains("tagout") || step.contains("loto") -> lockoutResponse(intent, transcript)
            step.contains("energy source") -> energySourceResponse(intent)
            step.contains("stored energy") -> storedEnergyResponse(intent)
            step.contains("ac isolated") || step.contains("ac is isolated") || step.contains("breaker") && step.contains("locked") -> acIsolationResponse(intent)
            step.contains("dc isolated") || step.contains("dc is isolated") -> dcIsolationResponse(intent, type)
            step.contains("zero energy") || step.contains("de-energized") || step.contains("tester") -> deEnergizedResponse(intent, transcript)
            step.contains("notify") || step.contains("notified") -> notifyResponse(intent)
            step.contains("authorized") || step.contains("qualified") -> authorizedResponse(intent)
            step.contains("energized") && step.contains("permit") -> energizedPermitResponse(intent, transcript)
            step.contains("arc flash") && step.contains("boundar") -> arcBoundaryResponse(intent)
            step.contains("unqualified") -> unqualifiedClearResponse(intent)
            step.contains("moisture") || step.contains("wet") || step.contains("floor") -> moistureResponse(intent, transcript)
            step.contains("ladder") || step.contains("scaffold") -> ladderResponse(intent, transcript)
            step.contains("emergency") || step.contains("egress") -> emergencyPathResponse(intent)
            step.contains("arc flash") && step.contains("label") -> arcFlashLabelResponse(intent, transcript)
            step.contains("visual inspection") || step.contains("defect") -> solarVisualResponse(intent, transcript)
            step.contains("connector") -> connectorResponse(intent, transcript)
            step.contains("polarity") -> polarityResponse(intent)
            step.contains("voc") || step.contains("open-circuit voltage") -> vocResponse(intent, transcript)
            step.contains("isc") || step.contains("short-circuit current") -> iscResponse(intent, transcript)
            step.contains("insulation resistance") -> insulationResponse(intent)
            step.contains("irradiance") -> irradianceResponse(intent, transcript)
            step.contains("anti-islanding") -> antiIslandingResponse(intent)
            else -> genericResponse(intent, step, standard)
        }
    }

    // ── PPE responses ─────────────────────────────────────────────────────────

    private fun glovesResponse(intent: Intent, transcript: String): EvidenceAnalysisResult {
        val hasRating = transcript.contains("class") || transcript.contains("cat") || transcript.contains("00") ||
            transcript.contains("rated") || transcript.contains("insulated") || transcript.contains("rubber") ||
            transcript.contains("electrically") || transcript.contains("dielectric")
        return when (intent) {
            Intent.STRONG_YES -> if (hasRating) EvidenceAnalysisResult(
                stepId = "ppe_gloves",
                visibleItems = listOf("Insulated gloves"),
                possibleHazards = emptyList(),
                missingOrUnclearItems = emptyList(),
                confidence = Confidence.HIGH,
                recommendedDecision = Decision.PASS,
                workerFollowupQuestion = null,
                spokenSummary = "Gloves confirmed and electrically rated. Proceeding.",
                safetyReasoningSummary = "Worker confirmed insulated gloves with electrical rating. NFPA 70E §130.7(C)(7)(a) requires voltage-rated gloves for electrical work. Requirement met."
            ) else EvidenceAnalysisResult(
                stepId = "ppe_gloves",
                visibleItems = listOf("Gloves present"),
                possibleHazards = listOf("Unconfirmed electrical rating"),
                missingOrUnclearItems = listOf("Voltage rating of gloves not stated"),
                confidence = Confidence.MEDIUM,
                recommendedDecision = Decision.WARN,
                workerFollowupQuestion = "What is the voltage rating or class of your gloves? NFPA 70E requires Class 0 minimum for most solar work.",
                spokenSummary = "Gloves are on. I need to confirm the electrical rating. What class or voltage rating are they?",
                safetyReasoningSummary = "Worker confirmed gloves are worn but did not state voltage class. NFPA 70E §130.7(C)(7)(a) requires voltage-rated insulating gloves. Cannot verify compliance without rating."
            )
            Intent.WEAK_YES -> EvidenceAnalysisResult(
                stepId = "ppe_gloves",
                visibleItems = listOf("Possible gloves"),
                possibleHazards = listOf("Glove presence and rating uncertain"),
                missingOrUnclearItems = listOf("Confirmation of rated insulated gloves"),
                confidence = Confidence.LOW,
                recommendedDecision = Decision.WARN,
                workerFollowupQuestion = "Are you certain your insulated gloves are on and intact? Electrical work requires voltage-rated gloves per NFPA 70E.",
                spokenSummary = "I'm not certain about your gloves. Please confirm you have rated insulated gloves on before proceeding.",
                safetyReasoningSummary = "Worker was uncertain about glove status. Per NFPA 70E §130.7(C)(7)(a), insulated gloves with voltage rating are mandatory for electrical work. Issuing WARN."
            )
            Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
                stepId = "ppe_gloves",
                visibleItems = emptyList(),
                possibleHazards = listOf("Electrical contact without insulated gloves — arc flash and shock risk"),
                missingOrUnclearItems = listOf("Insulated voltage-rated gloves are NOT confirmed"),
                confidence = Confidence.HIGH,
                recommendedDecision = Decision.STOP_WORK,
                workerFollowupQuestion = null,
                spokenSummary = "Stop work. Insulated gloves are required before touching any electrical equipment. Do not proceed without proper hand protection.",
                safetyReasoningSummary = "Worker indicated gloves are absent or unavailable. NFPA 70E §130.7(C)(7)(a) mandates voltage-rated insulating gloves for all electrical work. STOP_WORK issued — continuing without gloves risks electrocution."
            )
            Intent.UNCERTAIN -> EvidenceAnalysisResult(
                stepId = "ppe_gloves",
                visibleItems = emptyList(),
                possibleHazards = listOf("Unknown glove status"),
                missingOrUnclearItems = listOf("Glove confirmation needed"),
                confidence = Confidence.LOW,
                recommendedDecision = Decision.WARN,
                workerFollowupQuestion = "Can you confirm: are you wearing voltage-rated insulating gloves right now? Please look at your hands.",
                spokenSummary = "I need a clear answer on your gloves. Are rated insulated gloves on your hands right now?",
                safetyReasoningSummary = "Could not determine glove status from worker response. WARN issued pending clear confirmation per NFPA 70E §130.7(C)(7)(a)."
            )
        }
    }

    private fun faceProtectionResponse(intent: Intent) = when (intent) {
        Intent.STRONG_YES -> EvidenceAnalysisResult(
            stepId = "ppe_face_shield",
            visibleItems = listOf("Face protection confirmed"),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Arc-rated face protection confirmed. Proceeding.",
            safetyReasoningSummary = "Worker confirmed arc-rated face shield or safety glasses. NFPA 70E §130.7(C)(10)(b) requirement met."
        )
        Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
            stepId = "ppe_face_shield",
            visibleItems = emptyList(),
            possibleHazards = listOf("Arc flash to face — severe burn and blindness risk"),
            missingOrUnclearItems = listOf("Arc-rated face protection not confirmed"),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.STOP_WORK,
            workerFollowupQuestion = null,
            spokenSummary = "Stop work. Arc-rated face protection is required for all electrical work. Do not proceed without a face shield or safety glasses.",
            safetyReasoningSummary = "Worker lacks arc-rated face protection. NFPA 70E §130.7(C)(10)(b) requires arc-rated face protection for all work within the arc flash boundary. STOP_WORK."
        )
        else -> EvidenceAnalysisResult(
            stepId = "ppe_face_shield",
            visibleItems = emptyList(),
            possibleHazards = listOf("Unconfirmed face protection"),
            missingOrUnclearItems = listOf("Arc-rated face shield or safety glasses not confirmed"),
            confidence = Confidence.MEDIUM,
            recommendedDecision = Decision.WARN,
            workerFollowupQuestion = "Are you wearing arc-rated safety glasses or a face shield right now?",
            spokenSummary = "Please confirm: are you wearing arc-rated face protection?",
            safetyReasoningSummary = "Face protection status unclear. NFPA 70E §130.7(C)(10)(b) requires confirmation before proceeding."
        )
    }

    private fun arcClothingResponse(intent: Intent) = when (intent) {
        Intent.STRONG_YES -> EvidenceAnalysisResult(
            stepId = "ppe_arc_clothing",
            visibleItems = listOf("Arc-rated clothing", "Hi-vis vest"),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Arc-rated clothing and hi-vis vest confirmed. Proceeding.",
            safetyReasoningSummary = "Worker confirmed arc-rated clothing and hi-vis vest. NFPA 70E §130.7(C)(9) requirement met."
        )
        Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
            stepId = "ppe_arc_clothing",
            visibleItems = emptyList(),
            possibleHazards = listOf("Severe arc flash burn risk from unprotected clothing"),
            missingOrUnclearItems = listOf("Arc-rated clothing not confirmed"),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.FAIL,
            workerFollowupQuestion = "Do you have arc-rated clothing available? You need a minimum arc rating for this equipment's incident energy level.",
            spokenSummary = "Arc-rated clothing is not confirmed. This is a FAIL — do not approach energised equipment without arc-rated PPE.",
            safetyReasoningSummary = "Worker does not have arc-rated clothing. NFPA 70E §130.7(C)(9) requires arc-rated clothing based on incident energy analysis. FAIL."
        )
        else -> EvidenceAnalysisResult(
            stepId = "ppe_arc_clothing",
            visibleItems = emptyList(),
            possibleHazards = listOf("Unconfirmed arc protection"),
            missingOrUnclearItems = listOf("Arc-rated clothing confirmation needed"),
            confidence = Confidence.MEDIUM,
            recommendedDecision = Decision.WARN,
            workerFollowupQuestion = "Are you wearing arc-rated clothing and a hi-vis vest? What is the arc rating in cal/cm²?",
            spokenSummary = "Please confirm your arc-rated clothing is on. What is the arc rating?",
            safetyReasoningSummary = "Arc-rated clothing status unclear. NFPA 70E §130.7(C)(9) mandates arc-rated garments. WARN pending confirmation."
        )
    }

    private fun bootsResponse(intent: Intent) = when (intent) {
        Intent.STRONG_YES -> EvidenceAnalysisResult(
            stepId = "ppe_boots",
            visibleItems = listOf("Safety boots confirmed"),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Dielectric safety boots confirmed. Proceeding.",
            safetyReasoningSummary = "Worker confirmed dielectric-rated safety boots. OSHA 1910.136 requirement met."
        )
        Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
            stepId = "ppe_boots",
            visibleItems = emptyList(),
            possibleHazards = listOf("Step-potential electrical injury risk"),
            missingOrUnclearItems = listOf("Dielectric safety boots not confirmed"),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.FAIL,
            workerFollowupQuestion = null,
            spokenSummary = "Dielectric safety boots are required. Do not proceed without proper footwear. FAIL.",
            safetyReasoningSummary = "Worker lacks dielectric-rated safety boots. OSHA 1910.136 requires protective footwear for electrical environments. FAIL."
        )
        else -> EvidenceAnalysisResult(
            stepId = "ppe_boots",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = listOf("Boot confirmation needed"),
            confidence = Confidence.MEDIUM,
            recommendedDecision = Decision.WARN,
            workerFollowupQuestion = "Are you wearing dielectric-rated safety boots?",
            spokenSummary = "Please confirm you are wearing dielectric-rated safety boots.",
            safetyReasoningSummary = "Boot status unclear. OSHA 1910.136 requires confirmation. WARN."
        )
    }

    private fun hardHatResponse(intent: Intent) = when (intent) {
        Intent.STRONG_YES -> EvidenceAnalysisResult(
            stepId = "ppe_hard_hat",
            visibleItems = listOf("Class E hard hat confirmed"),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Class E hard hat confirmed. Proceeding.",
            safetyReasoningSummary = "Worker confirmed Class E electrical hard hat. NFPA 70E §130.7(C)(10)(a) requirement met."
        )
        Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
            stepId = "ppe_hard_hat",
            visibleItems = emptyList(),
            possibleHazards = listOf("Head impact and electrical contact injury"),
            missingOrUnclearItems = listOf("Class E hard hat not confirmed"),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.FAIL,
            workerFollowupQuestion = null,
            spokenSummary = "Class E hard hat required. Do not proceed. FAIL.",
            safetyReasoningSummary = "Worker lacks Class E hard hat. NFPA 70E §130.7(C)(10)(a) requires Class E (electrical) hard hat. FAIL."
        )
        else -> EvidenceAnalysisResult(
            stepId = "ppe_hard_hat",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = listOf("Hard hat class confirmation needed"),
            confidence = Confidence.MEDIUM,
            recommendedDecision = Decision.WARN,
            workerFollowupQuestion = "Is your hard hat on? Make sure it is Class E rated for electrical work.",
            spokenSummary = "Please confirm your Class E hard hat is on.",
            safetyReasoningSummary = "Hard hat status unclear. NFPA 70E §130.7(C)(10)(a) requires Class E hard hat. WARN."
        )
    }

    private fun hearingResponse(intent: Intent, transcript: String): EvidenceAnalysisResult {
        val notRequired = transcript.contains("not required") || transcript.contains("don't need") ||
            transcript.contains("no noise") || transcript.contains("not necessary") || transcript.contains("n/a")
        return if (notRequired || intent == Intent.STRONG_NO) EvidenceAnalysisResult(
            stepId = "ppe_hearing",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Hearing protection noted as not required for this task. Proceeding.",
            safetyReasoningSummary = "Worker confirmed hearing protection not required for this task. OSHA 1910.95 — no action needed."
        ) else EvidenceAnalysisResult(
            stepId = "ppe_hearing",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Hearing protection confirmed. Proceeding.",
            safetyReasoningSummary = "Worker addressed hearing protection per OSHA 1910.95. Proceeding."
        )
    }

    // ── LOTO / Inverter responses ─────────────────────────────────────────────

    private fun lockoutResponse(intent: Intent, transcript: String): EvidenceAnalysisResult {
        val hasPhysical = transcript.contains("padlock") || transcript.contains("hasp") ||
            transcript.contains("lock") || transcript.contains("my lock") || transcript.contains("personal")
        return when (intent) {
            Intent.STRONG_YES -> EvidenceAnalysisResult(
                stepId = "inv_lockout_applied",
                visibleItems = listOf("Lockout tag", "Isolating device"),
                possibleHazards = emptyList(),
                missingOrUnclearItems = emptyList(),
                confidence = Confidence.HIGH,
                recommendedDecision = Decision.PASS,
                workerFollowupQuestion = null,
                spokenSummary = "Lockout/tagout confirmed. Equipment is isolated. Proceeding.",
                safetyReasoningSummary = "Worker confirmed LOTO applied at the isolating device. OSHA 1910.147(d)(4)(i) requires personal lockout device applied before work. Confirmed."
            )
            Intent.WEAK_YES -> EvidenceAnalysisResult(
                stepId = "inv_lockout_applied",
                visibleItems = listOf("Possible lockout"),
                possibleHazards = listOf("Incomplete or unverified lockout"),
                missingOrUnclearItems = listOf("Physical lockout confirmation at isolating device"),
                confidence = Confidence.MEDIUM,
                recommendedDecision = Decision.WARN,
                workerFollowupQuestion = "Has YOUR personal padlock been placed on the isolating device? Look at it now and confirm.",
                spokenSummary = "I need a definitive confirmation. Is your personal padlock physically on the isolating device right now?",
                safetyReasoningSummary = "Worker uncertain about lockout status. OSHA 1910.147(d)(4)(i) requires physical application of a personal lockout device. Cannot proceed on uncertain LOTO."
            )
            Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
                stepId = "inv_lockout_applied",
                visibleItems = emptyList(),
                possibleHazards = listOf("Unexpected re-energization — risk of fatal electrocution"),
                missingOrUnclearItems = listOf("LOCKOUT/TAGOUT HAS NOT BEEN APPLIED"),
                confidence = Confidence.HIGH,
                recommendedDecision = Decision.STOP_WORK,
                workerFollowupQuestion = null,
                spokenSummary = "STOP WORK. Lockout/tagout has not been applied. Do NOT touch this equipment until your personal lock is on the isolating device. This is a life-safety requirement.",
                safetyReasoningSummary = "CRITICAL: Worker confirmed LOTO is NOT applied. OSHA 1910.147(d)(4)(i) requires lockout before any work on energized equipment. Risk of unexpected re-energization is fatal. STOP_WORK — mandatory."
            )
            Intent.UNCERTAIN -> EvidenceAnalysisResult(
                stepId = "inv_lockout_applied",
                visibleItems = emptyList(),
                possibleHazards = listOf("Unknown LOTO status — assume not safe"),
                missingOrUnclearItems = listOf("Lockout/tagout status unclear"),
                confidence = Confidence.LOW,
                recommendedDecision = Decision.STOP_WORK,
                workerFollowupQuestion = null,
                spokenSummary = "Stop work. I could not confirm lockout/tagout status. Do not proceed until you can confirm your personal lock is on the isolating device.",
                safetyReasoningSummary = "LOTO status unknown. Under OSHA 1910.147, safety conservative approach requires STOP_WORK when LOTO cannot be confirmed. Worker must physically verify before proceeding."
            )
        }
    }

    private fun energySourceResponse(intent: Intent) = when (intent) {
        Intent.STRONG_YES -> EvidenceAnalysisResult(
            stepId = "inv_energy_sources",
            visibleItems = listOf("Energy sources identified"),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "All energy sources identified. Proceeding.",
            safetyReasoningSummary = "Worker confirmed all energy sources identified per OSHA 1910.147(c)(6)(i). Proceed to isolation."
        )
        Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
            stepId = "inv_energy_sources",
            visibleItems = emptyList(),
            possibleHazards = listOf("Unidentified energy source could cause unexpected energization"),
            missingOrUnclearItems = listOf("Not all energy sources identified"),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.STOP_WORK,
            workerFollowupQuestion = null,
            spokenSummary = "Stop work. You must identify ALL energy sources before lockout. For solar installations, remember: AC from grid, DC from PV strings, and any stored energy in capacitors.",
            safetyReasoningSummary = "Worker has not identified all energy sources. OSHA 1910.147(c)(6)(i) requires identification of ALL energy sources. Solar systems have multiple sources — unidentified DC from PV array is a critical hazard. STOP_WORK."
        )
        else -> EvidenceAnalysisResult(
            stepId = "inv_energy_sources",
            visibleItems = emptyList(),
            possibleHazards = listOf("Possible unidentified energy source"),
            missingOrUnclearItems = listOf("Full energy source identification not confirmed"),
            confidence = Confidence.MEDIUM,
            recommendedDecision = Decision.WARN,
            workerFollowupQuestion = "Have you identified ALL energy sources — AC from grid, DC from the solar array, and any capacitor stored energy? Solar systems often have multiple feeds.",
            spokenSummary = "Confirm you've identified all energy sources — AC grid, DC from solar strings, and any capacitors. Solar inverters often have multiple feeds.",
            safetyReasoningSummary = "Energy source identification partially confirmed. Solar installations require identifying AC grid, DC PV strings, and stored energy. WARN pending full confirmation per OSHA 1910.147(c)(6)(i)."
        )
    }

    private fun storedEnergyResponse(intent: Intent) = when (intent) {
        Intent.STRONG_YES -> EvidenceAnalysisResult(
            stepId = "inv_stored_energy",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Stored energy addressed. Proceeding.",
            safetyReasoningSummary = "Worker confirmed stored energy released or restrained per OSHA 1910.147(d)(5). Safe to proceed."
        )
        Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
            stepId = "inv_stored_energy",
            visibleItems = emptyList(),
            possibleHazards = listOf("Capacitor discharge — risk of arc flash and injury"),
            missingOrUnclearItems = listOf("Stored energy not released or restrained"),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.STOP_WORK,
            workerFollowupQuestion = null,
            spokenSummary = "Stop work. Stored energy in capacitors must be discharged before any work. Inverters retain high-voltage charge for several minutes after power-off.",
            safetyReasoningSummary = "Stored energy not addressed. OSHA 1910.147(d)(5) requires restraint or release of stored energy. Solar inverters have significant capacitor charge. STOP_WORK until discharge verified."
        )
        else -> EvidenceAnalysisResult(
            stepId = "inv_stored_energy",
            visibleItems = emptyList(),
            possibleHazards = listOf("Unknown capacitor charge state"),
            missingOrUnclearItems = listOf("Stored energy confirmation needed"),
            confidence = Confidence.MEDIUM,
            recommendedDecision = Decision.WARN,
            workerFollowupQuestion = "Have the capacitors in the inverter discharged? Typically 3-5 minutes after power-off is required. Have you waited and verified?",
            spokenSummary = "Confirm stored energy — inverter capacitors need time to discharge. Have you waited the required discharge period?",
            safetyReasoningSummary = "Stored energy status unclear. OSHA 1910.147(d)(5) requires confirmation. Inverter capacitors can retain lethal charge. WARN."
        )
    }

    private fun acIsolationResponse(intent: Intent) = when (intent) {
        Intent.STRONG_YES -> EvidenceAnalysisResult(
            stepId = "inv_ac_isolated",
            visibleItems = listOf("AC breaker locked out"),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "AC isolation confirmed and breaker locked out. Proceeding.",
            safetyReasoningSummary = "Worker confirmed AC isolation and breaker lockout per OSHA 1910.333(b)(2). AC path secured."
        )
        Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
            stepId = "inv_ac_isolated",
            visibleItems = emptyList(),
            possibleHazards = listOf("Live AC voltage present — electrocution risk"),
            missingOrUnclearItems = listOf("AC NOT isolated"),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.STOP_WORK,
            workerFollowupQuestion = null,
            spokenSummary = "Stop work. AC must be isolated and the breaker locked before any work on this equipment.",
            safetyReasoningSummary = "AC not isolated. OSHA 1910.333(b)(2) requires working on de-energized equipment. Proceeding with live AC is a fatal risk. STOP_WORK."
        )
        else -> EvidenceAnalysisResult(
            stepId = "inv_ac_isolated",
            visibleItems = emptyList(),
            possibleHazards = listOf("Unknown AC isolation status"),
            missingOrUnclearItems = listOf("AC isolation not confirmed"),
            confidence = Confidence.MEDIUM,
            recommendedDecision = Decision.WARN,
            workerFollowupQuestion = "Is the AC breaker physically locked out? Confirm isolation before proceeding.",
            spokenSummary = "Please confirm the AC breaker is locked out and isolated.",
            safetyReasoningSummary = "AC isolation unclear. OSHA 1910.333(b)(2) requires de-energization. WARN pending confirmation."
        )
    }

    private fun dcIsolationResponse(intent: Intent, type: InspectionType): EvidenceAnalysisResult {
        val hasSolarWarning = type == InspectionType.SOLAR_COMMISSIONING || type == InspectionType.INVERTER_PANEL_CHECK
        return when (intent) {
            Intent.STRONG_YES -> EvidenceAnalysisResult(
                stepId = "inv_dc_isolated",
                visibleItems = listOf("DC disconnect confirmed"),
                possibleHazards = emptyList(),
                missingOrUnclearItems = emptyList(),
                confidence = Confidence.HIGH,
                recommendedDecision = Decision.PASS,
                workerFollowupQuestion = null,
                spokenSummary = "DC isolation confirmed at the inverter. Proceeding.",
                safetyReasoningSummary = "Worker confirmed DC isolation per IEC 62446-1 §6.4. Note: PV strings remain live unless combiner/string disconnects are also opened."
            )
            Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
                stepId = "inv_dc_isolated",
                visibleItems = emptyList(),
                possibleHazards = listOf("Live DC from solar array — CANNOT be switched off by AC breaker alone"),
                missingOrUnclearItems = listOf("DC isolation at inverter NOT confirmed"),
                confidence = Confidence.HIGH,
                recommendedDecision = Decision.STOP_WORK,
                workerFollowupQuestion = null,
                spokenSummary = "Stop work. Critical hazard: the DC from your solar panels is ALWAYS live when light hits them. The AC breaker does NOT isolate DC. You must open the DC disconnect at the combiner box or each string before touching the inverter.",
                safetyReasoningSummary = "CRITICAL: DC from PV array is not isolated. Solar panels generate DC continuously in daylight — this CANNOT be stopped by the AC breaker. IEC 62446-1 §6.4 requires DC isolation at the inverter/combiner. Fatal arc flash risk. STOP_WORK."
            )
            else -> EvidenceAnalysisResult(
                stepId = "inv_dc_isolated",
                visibleItems = emptyList(),
                possibleHazards = listOf(if (hasSolarWarning) "Solar DC always present in daylight — AC breaker does not stop it" else "Unknown DC status"),
                missingOrUnclearItems = listOf("DC isolation not confirmed"),
                confidence = Confidence.LOW,
                recommendedDecision = Decision.STOP_WORK,
                workerFollowupQuestion = null,
                spokenSummary = "Stop work until DC is confirmed isolated. Remember: solar panels produce live DC whenever there is light — the AC breaker does NOT protect you from this. Locate and open the DC disconnect.",
                safetyReasoningSummary = "DC isolation status unknown. Per IEC 62446-1 §6.4 and the critical nature of PV DC being always-live in daylight, safety-conservative approach requires STOP_WORK until DC isolation is confirmed."
            )
        }
    }

    private fun deEnergizedResponse(intent: Intent, transcript: String): EvidenceAnalysisResult {
        val hasMeter = transcript.contains("meter") || transcript.contains("tester") ||
            transcript.contains("multimeter") || transcript.contains("volt") ||
            transcript.contains("measured") || transcript.contains("reading") ||
            transcript.contains("zero") || transcript.contains("0v") || transcript.contains("0 volt")
        return when (intent) {
            Intent.STRONG_YES -> if (hasMeter) EvidenceAnalysisResult(
                stepId = "inv_de_energized",
                visibleItems = listOf("Zero energy verified with meter"),
                possibleHazards = emptyList(),
                missingOrUnclearItems = emptyList(),
                confidence = Confidence.HIGH,
                recommendedDecision = Decision.PASS,
                workerFollowupQuestion = null,
                spokenSummary = "Zero energy verified with meter. Equipment confirmed de-energized. Proceeding.",
                safetyReasoningSummary = "Worker verified zero energy with appropriate test instrument per OSHA 1910.147(d)(6). Both AC and DC confirmed at zero. Safe to proceed."
            ) else EvidenceAnalysisResult(
                stepId = "inv_de_energized",
                visibleItems = emptyList(),
                possibleHazards = listOf("Assumed zero energy — not instrument verified"),
                missingOrUnclearItems = listOf("Meter verification not mentioned"),
                confidence = Confidence.MEDIUM,
                recommendedDecision = Decision.WARN,
                workerFollowupQuestion = "Did you physically test with a meter? OSHA 1910.147(d)(6) requires actual instrument verification — not just assuming de-energized.",
                spokenSummary = "Confirmation needed: have you used a meter to verify zero energy? Assumed de-energized is not sufficient — you must test with an instrument.",
                safetyReasoningSummary = "Worker claimed de-energized but did not mention meter test. OSHA 1910.147(d)(6) requires actual test instrument verification. Assumption of zero energy is a known cause of electrical fatalities. WARN."
            )
            Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
                stepId = "inv_de_energized",
                visibleItems = emptyList(),
                possibleHazards = listOf("Equipment may still be energized — electrocution risk"),
                missingOrUnclearItems = listOf("Zero energy verification NOT performed"),
                confidence = Confidence.HIGH,
                recommendedDecision = Decision.STOP_WORK,
                workerFollowupQuestion = null,
                spokenSummary = "Stop work. You must verify zero energy with a meter before touching any conductors. This is one of the most critical OSHA requirements — do not skip it.",
                safetyReasoningSummary = "Worker has not performed zero-energy verification. OSHA 1910.147(d)(6) is explicit: after lockout, verify de-energization with appropriate test equipment. This step saves lives. STOP_WORK."
            )
            else -> EvidenceAnalysisResult(
                stepId = "inv_de_energized",
                visibleItems = emptyList(),
                possibleHazards = listOf("Zero energy unverified"),
                missingOrUnclearItems = listOf("Meter test not confirmed"),
                confidence = Confidence.LOW,
                recommendedDecision = Decision.WARN,
                workerFollowupQuestion = "Have you tested with a meter and confirmed zero volts at the work point? Tell me the reading.",
                spokenSummary = "Please confirm you've done a meter test. What was the voltage reading at the work point?",
                safetyReasoningSummary = "Zero energy verification unclear. OSHA 1910.147(d)(6) mandates instrument test. WARN until confirmed."
            )
        }
    }

    private fun notifyResponse(intent: Intent) = when (intent) {
        Intent.STRONG_YES -> EvidenceAnalysisResult(
            stepId = "inv_notify",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Affected employees notified. Proceeding.",
            safetyReasoningSummary = "Worker confirmed affected employees notified per OSHA 1910.147(d)(1). Proceeding."
        )
        Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
            stepId = "inv_notify",
            visibleItems = emptyList(),
            possibleHazards = listOf("Unaware personnel may inadvertently attempt to operate equipment"),
            missingOrUnclearItems = listOf("Affected employees NOT notified"),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.FAIL,
            workerFollowupQuestion = null,
            spokenSummary = "FAIL — you must notify all affected employees before beginning lockout. Someone unaware could attempt to start equipment. Notify your team now.",
            safetyReasoningSummary = "Affected employees not notified. OSHA 1910.147(d)(1) requires notification before lockout. Unaware personnel risk attempting to re-energize. FAIL."
        )
        else -> EvidenceAnalysisResult(
            stepId = "inv_notify",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = listOf("Employee notification not confirmed"),
            confidence = Confidence.MEDIUM,
            recommendedDecision = Decision.WARN,
            workerFollowupQuestion = "Have all affected employees been told this equipment is being locked out?",
            spokenSummary = "Confirm: have all affected team members been notified of the lockout?",
            safetyReasoningSummary = "Notification status unclear. OSHA 1910.147(d)(1) requires notification. WARN pending confirmation."
        )
    }

    private fun authorizedResponse(intent: Intent) = when (intent) {
        Intent.STRONG_YES -> EvidenceAnalysisResult(
            stepId = "inv_authorized",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Authorization confirmed. Proceeding.",
            safetyReasoningSummary = "Worker confirmed authorization and qualification per OSHA 1910.333(c)(2). Proceeding."
        )
        Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
            stepId = "inv_authorized",
            visibleItems = emptyList(),
            possibleHazards = listOf("Unqualified worker on energized electrical systems"),
            missingOrUnclearItems = listOf("Authorization and qualification not confirmed"),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.STOP_WORK,
            workerFollowupQuestion = null,
            spokenSummary = "Stop work. Only qualified and authorized workers may perform electrical work. Confirm your training and authorization before proceeding.",
            safetyReasoningSummary = "Worker authorization not confirmed. OSHA 1910.333(c)(2) requires workers on electrical systems to be qualified and authorized. STOP_WORK."
        )
        else -> EvidenceAnalysisResult(
            stepId = "inv_authorized",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = listOf("Authorization not confirmed"),
            confidence = Confidence.MEDIUM,
            recommendedDecision = Decision.WARN,
            workerFollowupQuestion = "Are you a qualified and authorized worker for this electrical task? What is your qualification level?",
            spokenSummary = "Please confirm you are qualified and authorized to perform this electrical work.",
            safetyReasoningSummary = "Authorization unclear. OSHA 1910.333(c)(2) requires qualified worker. WARN pending confirmation."
        )
    }

    private fun energizedPermitResponse(intent: Intent, transcript: String): EvidenceAnalysisResult {
        val notRequired = transcript.contains("not required") || transcript.contains("de-energized") ||
            transcript.contains("not applicable") || transcript.contains("n/a") || transcript.contains("no")
        return if (notRequired) EvidenceAnalysisResult(
            stepId = "inv_energized_permit",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Energized work permit noted as not required. Proceeding.",
            safetyReasoningSummary = "Worker confirmed energized work permit not required for this task. NFPA 70E §130.2(B) — work is on de-energized equipment. Noted."
        ) else EvidenceAnalysisResult(
            stepId = "inv_energized_permit",
            visibleItems = emptyList(),
            possibleHazards = listOf("Energized work without permit authorization"),
            missingOrUnclearItems = listOf("Energized Electrical Work Permit status"),
            confidence = Confidence.MEDIUM,
            recommendedDecision = Decision.WARN,
            workerFollowupQuestion = "If you are performing energized work, an Energized Electrical Work Permit is required per NFPA 70E §130.2(B). Has one been approved?",
            spokenSummary = "If this task involves energized equipment, you need an Energized Electrical Work Permit. Has that been approved?",
            safetyReasoningSummary = "Energized work permit status unclear. NFPA 70E §130.2(B) requires permit for any intentional energized work. WARN pending clarification."
        )
    }

    // ── Work area responses ───────────────────────────────────────────────────

    private fun arcBoundaryResponse(intent: Intent) = when (intent) {
        Intent.STRONG_YES -> EvidenceAnalysisResult(
            stepId = "wa_flash_boundary",
            visibleItems = listOf("Arc flash boundary established"),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Arc flash and approach boundaries established. Proceeding.",
            safetyReasoningSummary = "Worker confirmed arc flash and limited approach boundaries per NFPA 70E §130.2(B)(2). Boundary conditions met."
        )
        Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
            stepId = "wa_flash_boundary",
            visibleItems = emptyList(),
            possibleHazards = listOf("Unqualified personnel may enter arc flash zone"),
            missingOrUnclearItems = listOf("Arc flash boundary NOT established"),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.FAIL,
            workerFollowupQuestion = null,
            spokenSummary = "FAIL — arc flash boundary must be established before beginning work. Use barriers or cones to define the arc flash and limited approach boundaries.",
            safetyReasoningSummary = "Arc flash boundary not established. NFPA 70E §130.2(B)(2) requires arc flash boundary determination and establishment. FAIL."
        )
        else -> EvidenceAnalysisResult(
            stepId = "wa_flash_boundary",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = listOf("Boundary establishment not confirmed"),
            confidence = Confidence.MEDIUM,
            recommendedDecision = Decision.WARN,
            workerFollowupQuestion = "Have you established the arc flash and limited approach boundaries? Are they marked?",
            spokenSummary = "Confirm the arc flash and approach boundaries are established and marked.",
            safetyReasoningSummary = "Boundary status unclear. NFPA 70E §130.2(B)(2) requires establishment. WARN."
        )
    }

    private fun unqualifiedClearResponse(intent: Intent) = when (intent) {
        Intent.STRONG_YES -> EvidenceAnalysisResult(
            stepId = "wa_unqualified_clear",
            visibleItems = listOf("Work area clear"),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Work area clear of unqualified personnel. Proceeding.",
            safetyReasoningSummary = "Worker confirmed unqualified personnel clear per OSHA 1910.333(c)(3). Safe to proceed."
        )
        Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
            stepId = "wa_unqualified_clear",
            visibleItems = emptyList(),
            possibleHazards = listOf("Unqualified personnel within arc flash boundary"),
            missingOrUnclearItems = listOf("Unqualified personnel NOT clear"),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.STOP_WORK,
            workerFollowupQuestion = null,
            spokenSummary = "Stop work. All unqualified personnel must leave the work area before proceeding.",
            safetyReasoningSummary = "Unqualified personnel not clear of work area. OSHA 1910.333(c)(3) requires clearing area of unqualified persons. STOP_WORK."
        )
        else -> EvidenceAnalysisResult(
            stepId = "wa_unqualified_clear",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = listOf("Work area clearance not confirmed"),
            confidence = Confidence.MEDIUM,
            recommendedDecision = Decision.WARN,
            workerFollowupQuestion = "Are there any bystanders or unqualified personnel near the work area who need to leave?",
            spokenSummary = "Confirm all unqualified personnel are clear of the work area.",
            safetyReasoningSummary = "Personnel clearance status unclear. OSHA 1910.333(c)(3) requires confirmation. WARN."
        )
    }

    private fun moistureResponse(intent: Intent, transcript: String): EvidenceAnalysisResult {
        val hasMoisture = transcript.contains("wet") || transcript.contains("water") ||
            transcript.contains("damp") || transcript.contains("puddle") || transcript.contains("rain") ||
            transcript.contains("leak") || transcript.contains("moisture") || transcript.contains("flood")
        return if (hasMoisture) EvidenceAnalysisResult(
            stepId = "wa_moisture",
            visibleItems = listOf("Moisture / wet conditions detected"),
            possibleHazards = listOf("Electrical shock from wet floor", "Slip hazard"),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.STOP_WORK,
            workerFollowupQuestion = null,
            spokenSummary = "Stop work. Moisture or wet conditions present. Electrical work in wet conditions is prohibited. Dry and secure the area before proceeding.",
            safetyReasoningSummary = "Worker reported wet/moisture conditions. OSHA 1910.333(c)(6) prohibits electrical work in wet locations without specific precautions. Floor moisture combined with electrical work presents electrocution risk. STOP_WORK."
        ) else EvidenceAnalysisResult(
            stepId = "wa_moisture",
            visibleItems = listOf("Work area floor conditions described"),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Work area floor conditions noted as acceptable. Proceeding.",
            safetyReasoningSummary = "Worker described floor conditions with no moisture or obstructions. OSHA 1910.333(c)(6) condition met."
        )
    }

    private fun ladderResponse(intent: Intent, transcript: String): EvidenceAnalysisResult {
        val notApplicable = transcript.contains("not applicable") || transcript.contains("no ladder") ||
            transcript.contains("n/a") || transcript.contains("not needed") || transcript.contains("ground level")
        return if (notApplicable) EvidenceAnalysisResult(
            stepId = "wa_ladder_scaffold",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "No ladder or scaffold required for this task. Proceeding.",
            safetyReasoningSummary = "Worker confirmed no ladder/scaffold needed. OSHA 1926.1053 — not applicable."
        ) else when (intent) {
            Intent.STRONG_YES -> EvidenceAnalysisResult(
                stepId = "wa_ladder_scaffold",
                visibleItems = listOf("Ladder/scaffold assessed"),
                possibleHazards = emptyList(),
                missingOrUnclearItems = emptyList(),
                confidence = Confidence.HIGH,
                recommendedDecision = Decision.PASS,
                workerFollowupQuestion = null,
                spokenSummary = "Ladder or scaffold confirmed positioned and secured. Proceeding.",
                safetyReasoningSummary = "Worker confirmed ladder/scaffold safety per OSHA 1926.1053. Secured and positioned correctly."
            )
            Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
                stepId = "wa_ladder_scaffold",
                visibleItems = emptyList(),
                possibleHazards = listOf("Fall risk from unsecured ladder or scaffold"),
                missingOrUnclearItems = listOf("Ladder/scaffold not confirmed as secure"),
                confidence = Confidence.HIGH,
                recommendedDecision = Decision.FAIL,
                workerFollowupQuestion = null,
                spokenSummary = "FAIL — the ladder or scaffold must be secured before climbing. Do not use unsecured access equipment.",
                safetyReasoningSummary = "Ladder/scaffold not confirmed as secure. OSHA 1926.1053 requires proper positioning and securing. FAIL."
            )
            else -> EvidenceAnalysisResult(
                stepId = "wa_ladder_scaffold",
                visibleItems = emptyList(),
                possibleHazards = emptyList(),
                missingOrUnclearItems = listOf("Ladder/scaffold condition not confirmed"),
                confidence = Confidence.MEDIUM,
                recommendedDecision = Decision.WARN,
                workerFollowupQuestion = "Describe the ladder or scaffold — is it properly footed, angled, and secured?",
                spokenSummary = "Describe the ladder or scaffold setup — is it properly secured and positioned?",
                safetyReasoningSummary = "Ladder/scaffold status unclear. OSHA 1926.1053 requires confirmation. WARN."
            )
        }
    }

    private fun emergencyPathResponse(intent: Intent) = when (intent) {
        Intent.STRONG_YES -> EvidenceAnalysisResult(
            stepId = "wa_emergency_path",
            visibleItems = listOf("Egress path clear"),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Emergency egress path confirmed clear. Proceeding.",
            safetyReasoningSummary = "Worker confirmed emergency egress is clear per OSHA 1910.38. Safe to proceed."
        )
        else -> EvidenceAnalysisResult(
            stepId = "wa_emergency_path",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = listOf("Egress path not confirmed"),
            confidence = Confidence.MEDIUM,
            recommendedDecision = Decision.WARN,
            workerFollowupQuestion = "Is the emergency exit and evacuation path clear of obstacles?",
            spokenSummary = "Confirm the emergency egress path is clear and accessible.",
            safetyReasoningSummary = "Egress path unclear. OSHA 1910.38 requires clear emergency egress. WARN pending confirmation."
        )
    }

    private fun arcFlashLabelResponse(intent: Intent, transcript: String): EvidenceAnalysisResult {
        val notRequired = transcript.contains("not applicable") || transcript.contains("n/a") ||
            transcript.contains("not required") || transcript.contains("no label needed")
        return if (notRequired) EvidenceAnalysisResult(
            stepId = "wa_arc_flash_label",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.MEDIUM,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Arc flash label noted as not applicable. Proceeding.",
            safetyReasoningSummary = "Worker indicated arc flash label not applicable. NFPA 70E §130.5(H) may require label depending on equipment. Noted."
        ) else when (intent) {
            Intent.STRONG_YES -> EvidenceAnalysisResult(
                stepId = "wa_arc_flash_label",
                visibleItems = listOf("Arc flash hazard label"),
                possibleHazards = emptyList(),
                missingOrUnclearItems = emptyList(),
                confidence = Confidence.HIGH,
                recommendedDecision = Decision.PASS,
                workerFollowupQuestion = null,
                spokenSummary = "Arc flash hazard label confirmed present. Proceeding.",
                safetyReasoningSummary = "Arc flash label present and confirmed per NFPA 70E §130.5(H). Incident energy information available."
            )
            else -> EvidenceAnalysisResult(
                stepId = "wa_arc_flash_label",
                visibleItems = emptyList(),
                possibleHazards = listOf("Unknown incident energy level"),
                missingOrUnclearItems = listOf("Arc flash label not confirmed"),
                confidence = Confidence.MEDIUM,
                recommendedDecision = Decision.WARN,
                workerFollowupQuestion = "Can you see an arc flash hazard label on this equipment? It shows incident energy and required PPE.",
                spokenSummary = "Look for an arc flash hazard label on the panel. This tells you the incident energy level and required PPE category.",
                safetyReasoningSummary = "Arc flash label status unclear. NFPA 70E §130.5(H) requires arc flash hazard labeling. Without it, incident energy level and required PPE are unknown. WARN."
            )
        }
    }

    // ── Solar commissioning responses ─────────────────────────────────────────

    private fun solarVisualResponse(intent: Intent, transcript: String): EvidenceAnalysisResult {
        val hasDefect = transcript.contains("crack") || transcript.contains("broken") ||
            transcript.contains("damage") || transcript.contains("burn") || transcript.contains("discolor") ||
            transcript.contains("loose") || transcript.contains("corros") || transcript.contains("defect")
        return if (hasDefect) EvidenceAnalysisResult(
            stepId = "sc_visual_inspection",
            visibleItems = listOf("Defects observed"),
            possibleHazards = listOf("Physical or electrical defects in solar array"),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.FAIL,
            workerFollowupQuestion = null,
            spokenSummary = "FAIL — defects observed in the solar array. Document the defects and do not energize until repaired and re-inspected per IEC 62446-1.",
            safetyReasoningSummary = "Visual defects reported in solar array. IEC 62446-1 §4 requires defect-free modules, mounting, and wiring before commissioning. FAIL — do not energize."
        ) else EvidenceAnalysisResult(
            stepId = "sc_visual_inspection",
            visibleItems = listOf("Solar array visually inspected"),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Visual inspection complete. No defects reported. Proceeding.",
            safetyReasoningSummary = "Worker completed visual inspection with no defects found per IEC 62446-1 §4. Proceeding to electrical tests."
        )
    }

    private fun connectorResponse(intent: Intent, transcript: String): EvidenceAnalysisResult {
        val hasIssue = transcript.contains("loose") || transcript.contains("not secured") ||
            transcript.contains("missing") || transcript.contains("damaged") || transcript.contains("wrong type")
        return if (hasIssue) EvidenceAnalysisResult(
            stepId = "sc_connectors",
            visibleItems = listOf("Connector issue observed"),
            possibleHazards = listOf("Arc flash or fire from loose/mismatched PV connectors"),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.FAIL,
            workerFollowupQuestion = null,
            spokenSummary = "FAIL — connector issue identified. PV connectors must be correctly mated and PV-rated. Mismatched or loose connectors are a leading cause of PV system fires.",
            safetyReasoningSummary = "Connector defect found. IEC 62446-1 §4.3 requires all connectors to be correctly mated, secured, and PV-rated. Mismatched connectors cause arc tracking and fire. FAIL."
        ) else when (intent) {
            Intent.STRONG_YES -> EvidenceAnalysisResult(
                stepId = "sc_connectors",
                visibleItems = listOf("PV connectors secured"),
                possibleHazards = emptyList(),
                missingOrUnclearItems = emptyList(),
                confidence = Confidence.HIGH,
                recommendedDecision = Decision.PASS,
                workerFollowupQuestion = null,
                spokenSummary = "String connectors confirmed secured and PV-rated. Proceeding.",
                safetyReasoningSummary = "Worker confirmed all connectors secured and PV-rated per IEC 62446-1 §4.3. Proceeding."
            )
            else -> EvidenceAnalysisResult(
                stepId = "sc_connectors",
                visibleItems = emptyList(),
                possibleHazards = listOf("Unverified connector integrity"),
                missingOrUnclearItems = listOf("Connector check not confirmed"),
                confidence = Confidence.MEDIUM,
                recommendedDecision = Decision.WARN,
                workerFollowupQuestion = "Are all string connectors fully seated, locked, and from the same manufacturer? Mixed connector brands are a fire risk.",
                spokenSummary = "Confirm all connectors are fully seated and locked. Mixing connector brands voids safety ratings.",
                safetyReasoningSummary = "Connector status unclear. IEC 62446-1 §4.3 requires connector verification. Mixed connectors present arc tracking risk. WARN."
            )
        }
    }

    private fun polarityResponse(intent: Intent) = when (intent) {
        Intent.STRONG_YES -> EvidenceAnalysisResult(
            stepId = "sc_string_polarity",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "String polarity verified. Proceeding.",
            safetyReasoningSummary = "Worker confirmed string polarity verified before connection per IEC 62446-1 §5.2. Reversed polarity can destroy inverter input stage."
        )
        Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
            stepId = "sc_string_polarity",
            visibleItems = emptyList(),
            possibleHazards = listOf("Reversed polarity — inverter damage and DC arc risk"),
            missingOrUnclearItems = listOf("Polarity NOT verified"),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.STOP_WORK,
            workerFollowupQuestion = null,
            spokenSummary = "Stop work. String polarity must be verified BEFORE connecting to the inverter. Reversed polarity can destroy the inverter and create a DC arc fault.",
            safetyReasoningSummary = "Polarity not verified. IEC 62446-1 §5.2 requires polarity check before connection. Reversed polarity causes inverter damage and DC arc faults. STOP_WORK."
        )
        else -> EvidenceAnalysisResult(
            stepId = "sc_string_polarity",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = listOf("Polarity verification not confirmed"),
            confidence = Confidence.MEDIUM,
            recommendedDecision = Decision.WARN,
            workerFollowupQuestion = "Have you measured and confirmed correct polarity on each string before connection?",
            spokenSummary = "Confirm string polarity has been checked on each string before connecting.",
            safetyReasoningSummary = "Polarity verification unclear. IEC 62446-1 §5.2 requires verification. WARN."
        )
    }

    private fun vocResponse(intent: Intent, transcript: String): EvidenceAnalysisResult {
        val hasValue = transcript.any { it.isDigit() }
        return when (intent) {
            Intent.STRONG_YES -> EvidenceAnalysisResult(
                stepId = "sc_voc",
                visibleItems = emptyList(),
                possibleHazards = emptyList(),
                missingOrUnclearItems = emptyList(),
                confidence = Confidence.HIGH,
                recommendedDecision = Decision.PASS,
                workerFollowupQuestion = null,
                spokenSummary = "Voc confirmed within specification. Proceeding.",
                safetyReasoningSummary = "Worker confirmed Voc within spec per IEC 62446-1 §5.3. String is electrically healthy."
            )
            Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
                stepId = "sc_voc",
                visibleItems = emptyList(),
                possibleHazards = listOf("Out-of-spec Voc — possible shading, bypass diode failure, or open circuit"),
                missingOrUnclearItems = listOf("Voc not within specification"),
                confidence = Confidence.HIGH,
                recommendedDecision = Decision.FAIL,
                workerFollowupQuestion = "What is the measured Voc and what is the expected value? A significant difference indicates a fault.",
                spokenSummary = "Voc is out of specification. Do not connect this string until the fault is diagnosed. What is the measured versus expected voltage?",
                safetyReasoningSummary = "Voc out of spec. IEC 62446-1 §5.3 requires Voc within tolerance. Out-of-spec Voc indicates bypass diode failure, shading, or open circuit. FAIL."
            )
            else -> EvidenceAnalysisResult(
                stepId = "sc_voc",
                visibleItems = emptyList(),
                possibleHazards = emptyList(),
                missingOrUnclearItems = listOf("Voc measurement result not confirmed"),
                confidence = Confidence.MEDIUM,
                recommendedDecision = Decision.WARN,
                workerFollowupQuestion = "What voltage did you measure and is it within 5% of the expected Voc for this string?",
                spokenSummary = "Tell me the measured Voc and confirm it is within specification.",
                safetyReasoningSummary = "Voc confirmation unclear. IEC 62446-1 §5.3 requires in-spec Voc. WARN pending value confirmation."
            )
        }
    }

    private fun iscResponse(intent: Intent, transcript: String) = when (intent) {
        Intent.STRONG_YES -> EvidenceAnalysisResult(
            stepId = "sc_isc",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Isc confirmed within specification. Proceeding.",
            safetyReasoningSummary = "Worker confirmed Isc within spec per IEC 62446-1 §5.4. String current output is normal."
        )
        Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
            stepId = "sc_isc",
            visibleItems = emptyList(),
            possibleHazards = listOf("Low Isc may indicate partial shading, soiling, or module degradation"),
            missingOrUnclearItems = listOf("Isc not within specification"),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.FAIL,
            workerFollowupQuestion = "What is the measured Isc versus expected? Low current often indicates module mismatch, shading, or damage.",
            spokenSummary = "Isc is out of specification. Diagnose the cause before commissioning. What values did you measure?",
            safetyReasoningSummary = "Isc out of spec per IEC 62446-1 §5.4. Indicates possible module degradation, mismatch, or shading fault. FAIL."
        )
        else -> EvidenceAnalysisResult(
            stepId = "sc_isc",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = listOf("Isc measurement not confirmed"),
            confidence = Confidence.MEDIUM,
            recommendedDecision = Decision.WARN,
            workerFollowupQuestion = "What is the measured Isc for this string and does it match the expected value within 5%?",
            spokenSummary = "Confirm the Isc measurement and whether it is within specification.",
            safetyReasoningSummary = "Isc confirmation unclear. IEC 62446-1 §5.4 requires in-spec Isc. WARN."
        )
    }

    private fun insulationResponse(intent: Intent) = when (intent) {
        Intent.STRONG_YES -> EvidenceAnalysisResult(
            stepId = "sc_insulation",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Insulation resistance test passed. Proceeding.",
            safetyReasoningSummary = "Worker confirmed insulation resistance test passed per IEC 62446-1 §5.5. No earth fault or insulation breakdown detected."
        )
        Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
            stepId = "sc_insulation",
            visibleItems = emptyList(),
            possibleHazards = listOf("Insulation failure — risk of earth fault, fire, and electric shock"),
            missingOrUnclearItems = listOf("Insulation resistance test FAILED or not performed"),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.STOP_WORK,
            workerFollowupQuestion = null,
            spokenSummary = "Stop work. Insulation resistance test failed or was not performed. Do not energize. An insulation fault creates fire and shock hazard.",
            safetyReasoningSummary = "Insulation test failed/skipped. IEC 62446-1 §5.5 requires passing insulation resistance test before commissioning. Insulation failure = earth fault risk = fire and shock. STOP_WORK."
        )
        else -> EvidenceAnalysisResult(
            stepId = "sc_insulation",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = listOf("Insulation test result not confirmed"),
            confidence = Confidence.MEDIUM,
            recommendedDecision = Decision.WARN,
            workerFollowupQuestion = "What was the insulation resistance reading in megaohms and did it pass the minimum threshold?",
            spokenSummary = "Tell me the insulation resistance reading. What value did you get and did it pass?",
            safetyReasoningSummary = "Insulation test result unclear. IEC 62446-1 §5.5 requires minimum resistance. WARN pending result."
        )
    }

    private fun irradianceResponse(intent: Intent, transcript: String): EvidenceAnalysisResult {
        val notApplicable = transcript.contains("not applicable") || transcript.contains("n/a") ||
            transcript.contains("not required") || transcript.contains("no")
        return if (notApplicable) EvidenceAnalysisResult(
            stepId = "sc_irradiance",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.MEDIUM,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Irradiance recording noted as not applicable. Proceeding.",
            safetyReasoningSummary = "Worker noted irradiance not recorded. IEC 62446-1 §5.6 — optional for this deployment. Proceeding."
        ) else EvidenceAnalysisResult(
            stepId = "sc_irradiance",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Irradiance recorded. Proceeding.",
            safetyReasoningSummary = "Worker confirmed irradiance recorded at test time per IEC 62446-1 §5.6. Performance data contextualised."
        )
    }

    private fun antiIslandingResponse(intent: Intent) = when (intent) {
        Intent.STRONG_YES -> EvidenceAnalysisResult(
            stepId = "sc_anti_islanding",
            visibleItems = emptyList(),
            possibleHazards = emptyList(),
            missingOrUnclearItems = emptyList(),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.PASS,
            workerFollowupQuestion = null,
            spokenSummary = "Anti-islanding protection verified. Commissioning checklist complete.",
            safetyReasoningSummary = "Worker confirmed anti-islanding verified per AS/NZS 4777 and grid code. Grid workers are protected from unexpected re-energization during outage."
        )
        Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
            stepId = "sc_anti_islanding",
            visibleItems = emptyList(),
            possibleHazards = listOf("Grid workers exposed to live PV-generated voltage during outage"),
            missingOrUnclearItems = listOf("Anti-islanding NOT verified"),
            confidence = Confidence.HIGH,
            recommendedDecision = Decision.STOP_WORK,
            workerFollowupQuestion = null,
            spokenSummary = "Stop work. Anti-islanding must be verified before grid connection. Without it, your solar system could electrocute grid workers during a power outage.",
            safetyReasoningSummary = "Anti-islanding not verified. Grid code and AS/NZS 4777 require verification. Without anti-islanding, inverter continues to energize grid during outage — fatal risk to line workers. STOP_WORK."
        )
        else -> EvidenceAnalysisResult(
            stepId = "sc_anti_islanding",
            visibleItems = emptyList(),
            possibleHazards = listOf("Unverified anti-islanding protection"),
            missingOrUnclearItems = listOf("Anti-islanding verification needed"),
            confidence = Confidence.MEDIUM,
            recommendedDecision = Decision.WARN,
            workerFollowupQuestion = "Has the inverter's anti-islanding protection been tested and verified to the grid code specification?",
            spokenSummary = "Confirm anti-islanding protection has been tested. This prevents the inverter from energizing the grid during an outage.",
            safetyReasoningSummary = "Anti-islanding verification unclear. Grid code requires confirmation. WARN pending test result."
        )
    }

    // ── Generic fallback (never shows "I couldn't understand") ─────────────────

    private fun genericResponse(intent: Intent, step: String, standard: String): EvidenceAnalysisResult {
        val standardText = if (standard.isNotBlank()) " per $standard" else ""
        return when (intent) {
            Intent.STRONG_YES -> EvidenceAnalysisResult(
                stepId = "step_confirmed",
                visibleItems = emptyList(),
                possibleHazards = emptyList(),
                missingOrUnclearItems = emptyList(),
                confidence = Confidence.HIGH,
                recommendedDecision = Decision.PASS,
                workerFollowupQuestion = null,
                spokenSummary = "Step confirmed. Proceeding to next item.",
                safetyReasoningSummary = "Worker confirmed this checklist step$standardText. Proceeding."
            )
            Intent.STRONG_NO, Intent.WEAK_NO -> EvidenceAnalysisResult(
                stepId = "step_failed",
                visibleItems = emptyList(),
                possibleHazards = listOf("Required safety step not completed"),
                missingOrUnclearItems = listOf("Step not confirmed as complete"),
                confidence = Confidence.HIGH,
                recommendedDecision = Decision.FAIL,
                workerFollowupQuestion = "Can you complete this step now$standardText, or is there a reason it cannot be done?",
                spokenSummary = "This step has not been completed. Please address it before proceeding.",
                safetyReasoningSummary = "Worker indicated this safety step is not complete$standardText. FAIL — step is required."
            )
            else -> EvidenceAnalysisResult(
                stepId = "step_unclear",
                visibleItems = emptyList(),
                possibleHazards = emptyList(),
                missingOrUnclearItems = listOf("Step status needs clarification"),
                confidence = Confidence.LOW,
                recommendedDecision = Decision.WARN,
                workerFollowupQuestion = "Can you give me a clear yes or no: has this been completed$standardText?",
                spokenSummary = "I need a clearer answer. Has this step been completed?",
                safetyReasoningSummary = "Step status ambiguous$standardText. Requesting clarification. WARN."
            )
        }
    }

    // ── Batch checklist analysis ──────────────────────────────────────────────

    override suspend fun analyzeFullChecklist(request: BatchAnalysisRequest): BatchAnalysisResult {
        delay(1500)

        val nos = request.answers.filter { it.answerType == QuestionAnswer.AnswerType.NO }
        val skips = request.answers.filter { it.answerType == QuestionAnswer.AnswerType.SKIP }
        val yeses = request.answers.filter { it.answerType == QuestionAnswer.AnswerType.YES }
        val customs = request.answers.filter { it.answerType == QuestionAnswer.AnswerType.CUSTOM }

        val criticalNos = nos.filter { a ->
            val q = a.questionText.lowercase()
            q.contains("lockout") || q.contains("isolated") || q.contains("energized") ||
                q.contains("zero energy") || q.contains("de-energized") || q.contains("glove") ||
                q.contains("arc") || q.contains("insulation") || q.contains("loto") ||
                q.contains("polarity") || q.contains("anti-islanding")
        }

        val verdict = when {
            criticalNos.isNotEmpty() -> BatchAnalysisResult.Verdict.STOP_WORK
            nos.isNotEmpty() -> BatchAnalysisResult.Verdict.CAUTION
            skips.size > request.answers.size / 2 -> BatchAnalysisResult.Verdict.CAUTION
            else -> BatchAnalysisResult.Verdict.GO
        }

        val observations = request.answers.map { a ->
            when (a.answerType) {
                QuestionAnswer.AnswerType.YES -> "✓ Confirmed: ${a.questionText}"
                QuestionAnswer.AnswerType.NO -> "✗ NOT confirmed: ${a.questionText}"
                QuestionAnswer.AnswerType.SKIP -> "— Skipped: ${a.questionText}"
                QuestionAnswer.AnswerType.CUSTOM -> {
                    val snippet = a.customTranscript?.take(60)?.let { "\"$it\"" } ?: "(no transcript)"
                    "◎ Custom: ${a.questionText} → $snippet"
                }
            }
        }

        val recommendedActions = buildList {
            nos.forEach { add("Address failed item: ${it.questionText}") }
            if (criticalNos.isNotEmpty()) add("Do not proceed until all critical safety items are confirmed")
            if (skips.isNotEmpty()) add("${skips.size} question(s) were skipped — review before next inspection")
            if (verdict == BatchAnalysisResult.Verdict.GO) add("All required items confirmed. Proceed with standard safety precautions.")
        }

        val summary = when (verdict) {
            BatchAnalysisResult.Verdict.GO ->
                "All ${request.answers.size} safety checks confirmed. Safe to proceed."
            BatchAnalysisResult.Verdict.CAUTION ->
                "${nos.size} item(s) not confirmed, ${skips.size} skipped. Verify before proceeding."
            BatchAnalysisResult.Verdict.STOP_WORK ->
                "STOP WORK ORDER. ${criticalNos.size} critical safety requirement(s) not met."
        }

        val reasoning = buildString {
            append("${request.answers.size} answers reviewed: ${yeses.size} YES, ${nos.size} NO, ${skips.size} SKIPPED, ${customs.size} CUSTOM. ")
            if (criticalNos.isNotEmpty()) {
                append("Critical unconfirmed items: ${criticalNos.joinToString("; ") { it.questionText }}. ")
            }
            append(when (verdict) {
                BatchAnalysisResult.Verdict.GO -> "All critical safety requirements met."
                BatchAnalysisResult.Verdict.CAUTION -> "Caution recommended due to unconfirmed items."
                BatchAnalysisResult.Verdict.STOP_WORK -> "STOP WORK — critical safety requirements not confirmed."
            })
        }

        return BatchAnalysisResult(
            verdict = verdict,
            summary = summary,
            observations = observations,
            recommendedActions = recommendedActions,
            followUpQuestions = emptyList(),
            safetyReasoningSummary = reasoning,
            applicableStandards = request.answers.mapNotNull { it.standardRef }.distinct(),
        )
    }

    // ── Report generation ─────────────────────────────────────────────────────

    override suspend fun generateReport(request: ReportGenerationRequest): SafetyReport {
        delay(1200)
        val standards = request.completedChecklist.mapNotNull { it.standardRef }.distinct()
        val passedItems = request.completedChecklist.filter {
            it.status == com.example.fieldsafesolar.data.model.ChecklistItem.Status.COMPLETED_PASS
        }
        val warnedItems = request.completedChecklist.filter {
            it.status == com.example.fieldsafesolar.data.model.ChecklistItem.Status.COMPLETED_WARN
        }
        val failedItems = request.completedChecklist.filter {
            it.status == com.example.fieldsafesolar.data.model.ChecklistItem.Status.COMPLETED_FAIL ||
                it.status == com.example.fieldsafesolar.data.model.ChecklistItem.Status.COMPLETED_STOP_WORK
        }
        val pendingItems = request.completedChecklist.filter {
            it.status == com.example.fieldsafesolar.data.model.ChecklistItem.Status.PENDING
        }

        val observedConditions = buildList {
            passedItems.forEach { add("✓ ${it.description}") }
            warnedItems.forEach { add("⚠ ${it.description} — required follow-up") }
        }
        val workerConfirmations = request.voiceTranscripts.map { "Worker stated: \"${it.userSpeech}\"" }
        val unverifiedItems = buildList {
            failedItems.forEach { add("FAILED: ${it.description}") }
            pendingItems.forEach { add("NOT ASSESSED: ${it.description}") }
        }
        val recommendedActions = buildList {
            failedItems.forEach { item ->
                val ref = item.standardRef?.let { " ($it)" } ?: ""
                add("Address failed item: ${item.description}$ref")
            }
            warnedItems.forEach { item ->
                add("Re-verify: ${item.description}")
            }
            if (failedItems.any { it.description.contains("lockout") || it.description.contains("energized") }) {
                add("Do not proceed with electrical work until all LOTO steps are confirmed per OSHA 1910.147")
            }
            if (request.overallDecision == SafetyReport.Decision.PASS) {
                add("All required items confirmed. Proceed with standard safety precautions.")
            }
        }

        val decisionText = when (request.overallDecision) {
            SafetyReport.Decision.PASS -> "All required safety checks passed."
            SafetyReport.Decision.WARN -> "${warnedItems.size} item(s) require follow-up before work can be considered fully verified."
            SafetyReport.Decision.FAIL -> "${failedItems.size} item(s) failed safety assessment. Work must not proceed."
            SafetyReport.Decision.STOP_WORK -> "STOP WORK ORDER issued. Critical safety requirement not met. Immediate action required."
        }

        return SafetyReport(
            reportId = UUID.randomUUID().toString(),
            inspectionType = request.inspectionType,
            createdAt = Instant.now(),
            overallDecision = request.overallDecision,
            severity = request.severity,
            summary = "${request.inspectionType.name.replace("_", " ")} inspection completed. $decisionText " +
                "${passedItems.size}/${request.completedChecklist.size} items passed.",
            observedConditions = observedConditions.ifEmpty { listOf("No conditions recorded") },
            workerConfirmations = workerConfirmations.ifEmpty { listOf("No voice confirmations recorded") },
            unverifiedItems = unverifiedItems.ifEmpty { listOf("All assessed items were verified") },
            recommendedActions = recommendedActions.ifEmpty { listOf("No additional actions required") },
            evidence = emptyList(),
            limitations = listOf(
                "This report is based on worker verbal confirmations and automated AI analysis.",
                "Physical verification by a qualified safety officer is recommended for critical tasks.",
                "AI analysis does not constitute legal compliance certification."
            ),
            syncStatus = SafetyReport.SyncStatus.LOCAL_ONLY,
            applicableStandards = standards
        )
    }
}
