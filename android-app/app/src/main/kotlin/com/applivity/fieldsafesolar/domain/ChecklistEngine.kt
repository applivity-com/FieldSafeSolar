package com.applivity.fieldsafesolar.domain

import com.applivity.fieldsafesolar.data.model.ChecklistItem
import com.applivity.fieldsafesolar.data.model.InspectionType

interface ChecklistEngine {
    fun getChecklist(type: InspectionType): List<ChecklistItem>
    fun updateChecklistItemStatus(itemId: String, status: ChecklistItem.Status)
}