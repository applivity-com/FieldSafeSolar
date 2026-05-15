package com.example.fieldsafesolar.domain

import com.example.fieldsafesolar.data.model.ChecklistItem
import com.example.fieldsafesolar.data.model.InspectionType

interface ChecklistEngine {
    fun getChecklist(type: InspectionType): List<ChecklistItem>
    fun updateChecklistItemStatus(itemId: String, status: ChecklistItem.Status)
}