package com.applivity.fieldsafesolar.data.repository

import com.applivity.fieldsafesolar.data.model.ChecklistItem
import com.applivity.fieldsafesolar.data.model.ChecklistItem.ChecklistItemType
import com.applivity.fieldsafesolar.data.model.InspectionType
import com.applivity.fieldsafesolar.domain.ChecklistEngine

class LocalChecklistEngine : ChecklistEngine {

    private val checklists: MutableMap<InspectionType, MutableList<ChecklistItem>> = mutableMapOf()

    init {
        checklists[InspectionType.PPE_CHECK] = mutableListOf(
            ChecklistItem(
                id = "ppe_face_shield",
                description = "Confirm arc-rated face shield or safety glasses are on.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "NFPA 70E §130.7(C)(10)(b)"
            ),
            ChecklistItem(
                id = "ppe_gloves",
                description = "Look at your hands — confirm insulated gloves are on and intact.",
                type = ChecklistItemType.PHOTO_EVIDENCE,
                required = true,
                standardRef = "NFPA 70E §130.7(C)(7)(a)"
            ),
            ChecklistItem(
                id = "ppe_arc_clothing",
                description = "Confirm arc-rated clothing and hi-vis vest are on.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "NFPA 70E §130.7(C)(9)"
            ),
            ChecklistItem(
                id = "ppe_boots",
                description = "Confirm dielectric-rated safety boots are on.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "OSHA 1910.136"
            ),
            ChecklistItem(
                id = "ppe_hard_hat",
                description = "Confirm Class E electrical hard hat is on.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "NFPA 70E §130.7(C)(10)(a)"
            ),
            ChecklistItem(
                id = "ppe_hearing",
                description = "Is hearing protection required for this task?",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = false,
                standardRef = "OSHA 1910.95"
            )
        )

        checklists[InspectionType.INVERTER_PANEL_CHECK] = mutableListOf(
            ChecklistItem(
                id = "inv_notify",
                description = "Confirm affected employees have been notified of this work.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "OSHA 1910.147(d)(1)"
            ),
            ChecklistItem(
                id = "inv_energy_sources",
                description = "Confirm all energy sources for this equipment have been identified.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "OSHA 1910.147(c)(6)(i)"
            ),
            ChecklistItem(
                id = "inv_lockout_applied",
                description = "Look at the isolating device — confirm lockout/tagout tag is applied.",
                type = ChecklistItemType.PHOTO_EVIDENCE,
                required = true,
                standardRef = "OSHA 1910.147(d)(4)(i)"
            ),
            ChecklistItem(
                id = "inv_stored_energy",
                description = "Confirm stored energy (capacitors, springs) has been released or restrained.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "OSHA 1910.147(d)(5)"
            ),
            ChecklistItem(
                id = "inv_ac_isolated",
                description = "Confirm AC is isolated and breaker is locked out.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "OSHA 1910.333(b)(2)"
            ),
            ChecklistItem(
                id = "inv_dc_isolated",
                description = "Confirm DC is isolated at the inverter.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "IEC 62446-1 §6.4"
            ),
            ChecklistItem(
                id = "inv_de_energized",
                description = "Confirm zero energy verified with appropriate tester.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "OSHA 1910.147(d)(6)"
            ),
            ChecklistItem(
                id = "inv_energized_permit",
                description = "Is an Energized Electrical Work Permit required for this task?",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = false,
                standardRef = "NFPA 70E §130.2(B)"
            ),
            ChecklistItem(
                id = "inv_authorized",
                description = "Confirm worker is authorized and qualified for this electrical work.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "OSHA 1910.333(c)(2)"
            )
        )

        checklists[InspectionType.WORK_AREA_CHECK] = mutableListOf(
            ChecklistItem(
                id = "wa_flash_boundary",
                description = "Confirm arc flash and approach boundaries have been established.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "NFPA 70E §130.2(B)(2)"
            ),
            ChecklistItem(
                id = "wa_unqualified_clear",
                description = "Confirm unqualified personnel are clear of the work area.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "OSHA 1910.333(c)(3)"
            ),
            ChecklistItem(
                id = "wa_moisture",
                description = "Look at the work area floor — describe moisture, wet conditions, or obstructions.",
                type = ChecklistItemType.OPEN_ENDED,
                required = true,
                standardRef = "OSHA 1910.333(c)(6)"
            ),
            ChecklistItem(
                id = "wa_ladder_scaffold",
                description = "Look at the ladder or scaffold — confirm it is positioned and secured safely.",
                type = ChecklistItemType.PHOTO_EVIDENCE,
                required = false,
                standardRef = "OSHA 1926.1053"
            ),
            ChecklistItem(
                id = "wa_emergency_path",
                description = "Confirm emergency response and egress path is clear.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "OSHA 1910.38"
            ),
            ChecklistItem(
                id = "wa_arc_flash_label",
                description = "Look at the equipment panel — confirm arc flash hazard label is posted.",
                type = ChecklistItemType.PHOTO_EVIDENCE,
                required = false,
                standardRef = "NFPA 70E §130.5(H)"
            )
        )

        checklists[InspectionType.SOLAR_COMMISSIONING] = mutableListOf(
            ChecklistItem(
                id = "sc_visual_inspection",
                description = "Look at the solar array — describe any defects in modules, mounting, or wiring.",
                type = ChecklistItemType.OPEN_ENDED,
                required = true,
                standardRef = "IEC 62446-1 §4"
            ),
            ChecklistItem(
                id = "sc_connectors",
                description = "Look at the string connectors — confirm all are secured and PV-rated.",
                type = ChecklistItemType.PHOTO_EVIDENCE,
                required = true,
                standardRef = "IEC 62446-1 §4.3"
            ),
            ChecklistItem(
                id = "sc_string_polarity",
                description = "Confirm string polarity has been verified before connection.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "IEC 62446-1 §5.2"
            ),
            ChecklistItem(
                id = "sc_voc",
                description = "Confirm open-circuit voltage (Voc) is within spec for each string.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "IEC 62446-1 §5.3"
            ),
            ChecklistItem(
                id = "sc_isc",
                description = "Confirm short-circuit current (Isc) is within spec for each string.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "IEC 62446-1 §5.4"
            ),
            ChecklistItem(
                id = "sc_insulation",
                description = "Confirm insulation resistance test has passed.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "IEC 62446-1 §5.5"
            ),
            ChecklistItem(
                id = "sc_irradiance",
                description = "Confirm irradiance reading has been recorded at time of test.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = false,
                standardRef = "IEC 62446-1 §5.6"
            ),
            ChecklistItem(
                id = "sc_anti_islanding",
                description = "Confirm inverter anti-islanding protection has been verified.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "AS/NZS 4777 / Grid code"
            ),
            ChecklistItem(
                id = "sc_rapid_shutdown",
                description = "Rapid Shutdown verified: activate shutdown switch and confirm all PV conductors de-energize to ≤30V outside the array boundary (or ≤80V inside) within 30 seconds.",
                type = ChecklistItemType.PHOTO_EVIDENCE,
                required = true,
                standardRef = "NEC 690.12(B)(2) / 690.12(B)(3)"
            ),
            ChecklistItem(
                id = "sc_afci",
                description = "Arc Fault Circuit Interrupter (AFCI) protection confirmed for all DC PV circuits operating at ≥80Vdc — device listed, installed, and functionally tested.",
                type = ChecklistItemType.VERBAL_CONFIRMATION,
                required = true,
                standardRef = "NEC 690.11"
            )
        )
    }

    override fun getChecklist(type: InspectionType): List<ChecklistItem> {
        return checklists[type]?.toList() ?: emptyList()
    }

    override fun updateChecklistItemStatus(itemId: String, status: ChecklistItem.Status) {
        checklists.values.forEach { checklist ->
            checklist.find { it.id == itemId }?.let { item ->
                item.status = status
                return
            }
        }
    }
}
