/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-28.
 */

package pl.szczodrzynski.edziennik.data.api.edziennik.idziennik.data.web

import android.graphics.Color
import pl.szczodrzynski.edziennik.*
import pl.szczodrzynski.edziennik.data.api.ERROR_IDZIENNIK_WEB_REQUEST_NO_DATA
import pl.szczodrzynski.edziennik.data.api.IDZIENNIK_WEB_GRADES
import pl.szczodrzynski.edziennik.data.api.edziennik.idziennik.DataIdziennik
import pl.szczodrzynski.edziennik.data.api.edziennik.idziennik.ENDPOINT_IDZIENNIK_WEB_GRADES
import pl.szczodrzynski.edziennik.data.api.edziennik.idziennik.data.IdziennikWeb
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.data.api.models.DataRemoveModel
import pl.szczodrzynski.edziennik.data.db.entity.Grade
import pl.szczodrzynski.edziennik.data.db.entity.Grade.Companion.TYPE_NORMAL
import pl.szczodrzynski.edziennik.data.db.entity.Metadata
import pl.szczodrzynski.edziennik.data.db.entity.SYNC_ALWAYS
import pl.szczodrzynski.edziennik.utils.models.Date

class IdziennikWebGrades(override val data: DataIdziennik,
                         override val lastSync: Long?,
                         val onSuccess: (endpointId: Int) -> Unit
) : IdziennikWeb(data, lastSync) {
    companion object {
        private const val TAG = "IdziennikWebGrades"
    }

    init { data.profile?.also { profile ->
        webApiGet(TAG, IDZIENNIK_WEB_GRADES, mapOf(
                "idPozDziennika" to data.registerId
        )) { result ->
            val json = result.getJsonObject("d") ?: run {
                data.error(ApiError(TAG, ERROR_IDZIENNIK_WEB_REQUEST_NO_DATA)
                        .withApiResponse(result))
                return@webApiGet
            }

            json.getJsonArray("Przedmioty")?.asJsonObjectList()?.onEach { subjectJson ->
                val subject = data.getSubject(
                        subjectJson.getString("Przedmiot") ?: return@onEach,
                        subjectJson.getLong("IdPrzedmiotu") ?: return@onEach,
                        subjectJson.getString("Przedmiot") ?: return@onEach
                )
                subjectJson.getJsonArray("Oceny")?.asJsonObjectList()?.forEach { grade ->
                    val id = grade.getLong("idK") ?: return@forEach
                    val category = grade.getString("Kategoria") ?: ""
                    val name = grade.getString("Ocena") ?: "?"
                    val semester = grade.getInt("Semestr") ?: 1
                    val teacher = data.getTeacherByLastFirst(grade.getString("Wystawil") ?: return@forEach)

                    val countToAverage = grade.getBoolean("DoSredniej") ?: true
                    var value = grade.getFloat("WartoscDoSred") ?: 0.0f
                    val weight = if (countToAverage)
                        grade.getFloat("Waga") ?: 0.0f
                    else
                        0.0f

                    val gradeColor = grade.getString("Kolor") ?: ""
                    var colorInt = 0xff2196f3.toInt()
                    if (gradeColor.isNotEmpty()) {
                        colorInt = Color.parseColor("#$gradeColor")
                    }

                    val addedDate = grade.getString("Data_wystaw")?.let { Date.fromY_m_d(it).inMillis } ?: System.currentTimeMillis()

                    val gradeObject = Grade(
                            profileId = profileId,
                            id = id,
                            name = name,
                            type = TYPE_NORMAL,
                            value = value,
                            weight = weight,
                            color = colorInt,
                            category = category,
                            description = null,
                            comment = null,
                            semester = semester,
                            teacherId = teacher.id,
                            subjectId = subject.id,
                            addedDate = addedDate
                    )

                    when (grade.getInt("Typ")) {
                        0 -> {
                            val history = grade.getJsonArray("Historia")?.asJsonObjectList()
                            if (history?.isNotEmpty() == true) {
                                var sum = gradeObject.value * gradeObject.weight
                                var count = gradeObject.weight
                                for (historyItem in history) {
                                    val countToTheAverage = historyItem.getBoolean("DoSredniej") ?: false
                                    value = historyItem.get("WartoscDoSred").asFloat
                                    val weight = historyItem.get("Waga").asFloat

                                    if (value > 0 && countToTheAverage) {
                                        sum += value * weight
                                        count += weight
                                    }

                                    val historyColor = historyItem.getString("Kolor") ?: ""
                                    colorInt = 0xff2196f3.toInt()
                                    if (historyColor.isNotEmpty()) {
                                        colorInt = Color.parseColor("#$historyColor")
                                    }

                                    val addedDate = historyItem.getString("Data_wystaw")?.let { Date.fromY_m_d(it).inMillis } ?: System.currentTimeMillis()

                                    val historyObject = Grade(
                                            profileId = profileId,
                                            id = gradeObject.id * -1,
                                            name = historyItem.getString("Ocena") ?: "",
                                            type = TYPE_NORMAL,
                                            value = value,
                                            weight = if (value > 0f && countToTheAverage) weight * -1f else 0f,
                                            color = colorInt,
                                            category = historyItem.getString("Kategoria"),
                                            description = historyItem.getString("Uzasadnienie"),
                                            comment = null,
                                            semester = historyItem.getInt("Semestr") ?: 1,
                                            teacherId = teacher.id,
                                            subjectId = subject.id,
                                            addedDate = addedDate
                                    )
                                    historyObject.parentId = gradeObject.id

                                    data.gradeList.add(historyObject)
                                    data.metadataList.add(Metadata(
                                            profileId,
                                            Metadata.TYPE_GRADE,
                                            historyObject.id,
                                            true,
                                            true
                                    ))
                                }
                                // update the current grade's value with an average of all historical grades and itself
                                if (sum > 0 && count > 0) {
                                    gradeObject.value = sum / count
                                }
                                gradeObject.isImprovement = true // gradeObject is the improved grade. Originals are historyObjects
                            }
                        }
                        1 -> {
                            gradeObject.type = Grade.TYPE_SEMESTER1_FINAL
                            gradeObject.name = value.toInt().toString()
                            gradeObject.weight = 0f
                        }
                        2 -> {
                            gradeObject.type = Grade.TYPE_YEAR_FINAL
                            gradeObject.name = value.toInt().toString()
                            gradeObject.weight = 0f
                        }
                    }

                    data.gradeList.add(gradeObject)
                    data.metadataList.add(
                            Metadata(
                                    profileId,
                                    Metadata.TYPE_GRADE,
                                    id,
                                    data.profile.empty,
                                    data.profile.empty
                            ))
                }
            }

            data.toRemove.addAll(listOf(
                    Grade.TYPE_NORMAL,
                    Grade.TYPE_SEMESTER1_FINAL,
                    Grade.TYPE_YEAR_FINAL
            ).map {
                DataRemoveModel.Grades.semesterWithType(profile.currentSemester, it)
            })
            data.setSyncNext(ENDPOINT_IDZIENNIK_WEB_GRADES, SYNC_ALWAYS)
            onSuccess(ENDPOINT_IDZIENNIK_WEB_GRADES)
        }
    } ?: onSuccess(ENDPOINT_IDZIENNIK_WEB_GRADES) }
}