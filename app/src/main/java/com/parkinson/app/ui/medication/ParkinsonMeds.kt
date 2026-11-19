package com.parkinson.app.ui.medication

data class MedItem(val name: String, val doseMg: Float)

object ParkinsonMeds {
    val list: List<MedItem> = listOf(
        MedItem("Levodopa/Carbidopa", 100f),
        MedItem("Pramipexol", 0.25f),
        MedItem("Ropinirol", 1f),
        MedItem("Rasagilina", 1f),
        MedItem("Selegilina", 5f),
        MedItem("Amantadina", 100f),
        MedItem("Entacapona", 200f),
        MedItem("Tolcapona", 100f),
        MedItem("Rotigotina", 2f),
        MedItem("Apomorfina", 2f)
    )
}
