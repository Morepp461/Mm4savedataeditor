package com.morepp.mm4savedataeditor

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTreeEditorTest {
    @Test
    fun editsNestedObjectAndArrayValues() {
        val root = JsonObject().apply {
            add("career", JsonObject().apply { addProperty("season", 2) })
            add("names", JsonArray().apply { add("Alpha"); add("Beta") })
        }

        assertTrue(setPath(root, listOf("career", "season"), JsonPrimitive(3)))
        assertTrue(appendChild(root, listOf("names"), null, JsonPrimitive("Gamma")))
        assertTrue(removePath(root, listOf("names", "[0]")))

        assertEquals(3, root.getAsJsonObject("career").get("season").asInt)
        assertEquals(2, root.getAsJsonArray("names").size())
        assertEquals("Beta", root.getAsJsonArray("names")[0].asString)
        assertEquals("Gamma", root.getAsJsonArray("names")[1].asString)
    }

    @Test
    fun objectAppendRejectsDuplicateProperty() {
        val root = JsonObject().apply { addProperty("balance", 100) }

        assertFalse(appendChild(root, emptyList(), "balance", JsonPrimitive(200)))
        assertEquals(100, root.get("balance").asInt)
    }

    @Test
    fun flattenIncludesContainersAndNestedLeaves() {
        val root = JsonObject().apply {
            add("team", JsonObject().apply { addProperty("name", "Apex") })
        }

        val paths = flatten(root).map { it.first.joinToString("/") }

        assertTrue(paths.contains("team"))
        assertTrue(paths.contains("team/name"))
    }

    @Test
    fun mutateDocumentCopiesTheEditedRoot() {
        val original = Mm4SaveCodec.Document(
            JsonObject().apply { addProperty("career", "original") },
            JsonObject().apply { addProperty("balance", 100) },
            "copy-test.msv"
        )

        val edited = mutateDocument(original, "Game Data") {
            setPath(it, listOf("balance"), JsonPrimitive(250))
        }

        assertNotNull(edited)
        assertEquals(100, original.data.get("balance").asInt)
        assertEquals(250, edited!!.data.get("balance").asInt)
        assertEquals("original", original.info.get("career").asString)
    }

    @Test
    fun teamCatalogOnlyAllowsIdentityReputationAndFinancialFields() {
        assertTrue(isCatalogPrimitiveEditable("team", listOf("name")))
        assertTrue(isCatalogPrimitiveEditable("team", listOf("shortName")))
        assertTrue(isCatalogPrimitiveEditable("team", listOf("m_Reputation")))
        assertTrue(isCatalogPrimitiveEditable("team", listOf("m_Financials", "m_Balance")))
        assertFalse(isCatalogPrimitiveEditable("team", listOf("aggression")))
        assertFalse(isCatalogPrimitiveEditable("team", listOf("m_EngineerRacePerformanceModifier")))
    }

    @Test
    fun staffCatalogOnlyAllowsProfileContractAndCompensationFields() {
        assertTrue(isCatalogPrimitiveEditable("staff", listOf("m_PersonDetails", "name")))
        assertTrue(isCatalogPrimitiveEditable("staff", listOf("contract", "m_PerRaceCost")))
        assertTrue(isCatalogPrimitiveEditable("staff", listOf("m_Salary")))
        assertFalse(isCatalogPrimitiveEditable("staff", listOf("m_ExperiencePoints")))
        assertFalse(isCatalogPrimitiveEditable("staff", listOf("stats", "m_Stats", "[0]", "m_Stat")))
    }

    @Test
    fun carCatalogOnlyExposesItsDedicatedAttributeCards() {
        assertFalse(isCatalogPrimitiveEditable("car", listOf("m_Stats", "[0]")))
        assertFalse(isCatalogPrimitiveEditable("car", listOf("m_EngineLevel")))
    }

    @Test
    fun teamFinancialWhitelistDoesNotLeakNestedCarFields() {
        assertTrue(isCatalogPrimitiveEditable("team", listOf("m_Financials", "m_Balance")))
        assertFalse(isCatalogPrimitiveEditable("team", listOf("car", "m_MaintenanceCost")))
        assertFalse(isCatalogPrimitiveEditable("team", listOf("driver", "m_Salary")))
    }

    @Test
    fun staffCompensationWhitelistDoesNotLeakNestedAttributeFields() {
        assertTrue(isCatalogPrimitiveEditable("staff", listOf("m_Salary")))
        assertFalse(isCatalogPrimitiveEditable("staff", listOf("stats", "m_Salary")))
    }

    @Test
    fun numericSteppingPreservesLargeIntegerPrecisionAndDecimals() {
        assertEquals("9007199254740994", stepNumericValue("9007199254740993", 1.0, 1))
        assertEquals("2.5", stepNumericValue("1.5", 1.0, 1))
        assertEquals("0.25", stepNumericValue("0.5", 0.25, -1))
    }

    @Test
    fun driverAgeUsesCareerDateInsteadOfPhoneDate() {
        assertEquals("29", ageFromDate("1990-06-10", "2020-06-09"))
        assertEquals("30", ageFromDate("1990-06-10", "2020-06-10"))
    }

    @Test
    fun financeHistorySupportsPrimitiveAndObjectRecords() {
        assertEquals("£1,250", financeHistoryLabel(JsonPrimitive(1250)))
        val record = JsonObject().apply { addProperty("amount", 8750) }
        assertEquals("£8,750", financeHistoryLabel(record))
    }
    @Test
    fun driverTransferUpdatesContractAndExistingTeamRoster() {
        val data = JsonObject().apply {
            add("teamManager", JsonObject().apply {
                add("m_Teams", JsonArray().apply { add(JsonObject().apply { addProperty("\$ref", "team-a") }); add(JsonObject().apply { addProperty("\$ref", "team-b") }) })
            })
            add("entities", JsonArray().apply {
                add(JsonObject().apply { addProperty("\$id", "team-a"); add("m_Drivers", JsonArray().apply { add(JsonObject().apply { addProperty("\$ref", "driver-1") }) }) })
                add(JsonObject().apply { addProperty("\$id", "team-b"); add("m_Drivers", JsonArray()) })
                add(JsonObject().apply {
                    addProperty("\$id", "driver-1")
                    add("contract", JsonObject().apply { add("m_Team", JsonObject().apply { addProperty("\$ref", "team-a") }) })
                })
            })
        }
        assertTrue(moveDriverToTeam(data, "driver-1", "team-b"))
        val driver = collectObjectsWithId(data).first { it.second.get("\$id").asString == "driver-1" }.second
        assertEquals("team-b", driver.getAsJsonObject("contract").getAsJsonObject("m_Team").get("\$ref").asString)
        val oldRoster = collectObjectsWithId(data).first { it.second.get("\$id").asString == "team-a" }.second.getAsJsonArray("m_Drivers")
        val newRoster = collectObjectsWithId(data).first { it.second.get("\$id").asString == "team-b" }.second.getAsJsonArray("m_Drivers")
        assertEquals(0, oldRoster.size())
        assertEquals("driver-1", newRoster[0].asJsonObject.get("\$ref").asString)
    }

    @Test
    fun profileDisplayHidesGeneratedTweetHandlesAndFormatsBirthDate() {
        assertEquals("Belum diatur", driverProfileDisplayValue("tweetHandle", JsonPrimitive("MMM_TweetHandles_10000001")))
        assertEquals("30 Jul 1993", driverProfileDisplayValue("dateOfBirth", JsonPrimitive("1993-07-30T00:00:00.0000000")))
    }

    @Test
    fun displayedAttributeValuesAreRoundedToIntegers() {
        assertEquals("14", displayIntegerStat(JsonPrimitive(13.7)))
        assertEquals("13", displayIntegerStat(JsonPrimitive(13.2)))
    }

    @Test
    fun divisionRenameOnlyChangesKnownDivisionNames() {
        val data = JsonObject().apply {
            add("championshipManager", JsonObject().apply {
                add("divisions", JsonArray().apply {
                    add(JsonObject().apply { addProperty("name", "World Motorsport") })
                    add(JsonObject().apply { addProperty("name", "Asia Pacific Super Cup") })
                    add(JsonObject().apply { addProperty("name", "Unrelated Series") })
                })
            })
        }
        assertEquals(2, renameCareerDivisions(data))
        val divisions = data.getAsJsonObject("championshipManager").getAsJsonArray("divisions")
        assertEquals("F1", divisions[0].asJsonObject.get("name").asString)
        assertEquals("F2", divisions[1].asJsonObject.get("name").asString)
        assertEquals("Unrelated Series", divisions[2].asJsonObject.get("name").asString)
    }

    @Test
    fun headquartersLevelMaximumIsDerivedFromSavedUpgradeNodes() {
        val document = Mm4SaveCodec.Document(
            JsonObject(),
            JsonObject().apply {
                add("headquarters", JsonObject().apply {
                    add("m_CurrentTierInformation", JsonArray().apply {
                        add(JsonObject().apply { addProperty("Key", 0); addProperty("Value", 1) })
                    })
                    add("m_Upgrades", JsonArray().apply {
                        repeat(3) { add(JsonObject().apply { add("Value", JsonObject().apply { addProperty("pathType", 0) }) }) }
                        add(JsonObject().apply { add("Value", JsonObject().apply { addProperty("pathType", 1) }) })
                    })
                })
            },
            "hq-bounds.msv"
        )
        val bounds = editorBounds(document, "Game Data", listOf("headquarters","m_CurrentTierInformation","[0]","Value"))
        assertEquals("0", bounds.first)
        assertEquals("3", bounds.second)
    }

    @Test
    fun knownHqStateAndAttributeEditorsHaveHardLimits() {
        val document = Mm4SaveCodec.Document(JsonObject(), JsonObject(), "bounds.msv")
        assertEquals("2", editorBounds(document, "Game Data", listOf("headquarters","m_Upgrades","[0]","Value","State")).second)
        assertEquals("20", editorBounds(document, "Game Data", listOf("driver","m_Stats","m_Stats","[0]","m_Stat")).second)
    }


    @Test
    fun f1F4TemplateImportsDriverProfilesAndPreservesCareerTeamIdentity() {
        fun fixture():JsonObject {
            val championshipRefs=JsonArray()
            val entityRows=JsonArray()
            repeat(4) { division ->
                val championshipId="champ-$division"
                championshipRefs.add(JsonObject().apply { addProperty("\$ref",championshipId) })
                val numericTeamIds=JsonArray()
                repeat(10) { teamIndex ->
                    val numericTeamId=division*10+teamIndex+1
                    val teamEntityId="team-$numericTeamId"
                    numericTeamIds.add(numericTeamId)
                    val driverRows=JsonArray()
                    repeat(2) { driverIndex ->
                        val driverEntityId="driver-$numericTeamId-$driverIndex"
                        driverRows.add(JsonObject().apply {
                            add("driver",JsonObject().apply { addProperty("\$ref",driverEntityId) })
                        })
                        entityRows.add(JsonObject().apply {
                            addProperty("\$id",driverEntityId)
                            add("contract",JsonObject().apply {
                                add("m_Team",JsonObject().apply { addProperty("\$ref",teamEntityId) })
                            })
                            add("m_PersonDetails",JsonObject().apply {
                                addProperty("name","Original Driver $numericTeamId-$driverIndex")
                                add("nationality",JsonObject().apply { addProperty("mNationalityID",1) })
                            })
                            add("m_Stats",JsonObject().apply { add("m_Stats",JsonArray().apply { add(JsonObject().apply { addProperty("m_Stat",1) }) }) })
                            add("improvement",JsonObject().apply { add("m_Driver",JsonObject().apply { addProperty("\$ref",driverEntityId) }) })
                        })
                    }
                    entityRows.add(JsonObject().apply {
                        addProperty("\$id",teamEntityId)
                        addProperty("m_TeamID",numericTeamId)
                        addProperty("name","Original Team $numericTeamId")
                        addProperty("shortName","Original")
                        add("m_TeamDriverData",JsonObject().apply { add("m_Data",driverRows) })
                    })
                }
                entityRows.add(JsonObject().apply {
                    addProperty("\$id",championshipId)
                    addProperty("m_ID",division)
                    add("m_TeamIDs",numericTeamIds)
                })
            }
            return JsonObject().apply {
                add("championshipManager",JsonObject().apply { add("championships",championshipRefs) })
                add("entities",entityRows)
            }
        }

        val target=fixture()
        val template=fixture()
        val sourceById=collectObjectsWithId(template).associate { it.second.get("\$id").asString to it.second }
        for(division in 0..3) {
            for(teamIndex in 0 until 10) {
                val teamId=division*10+teamIndex+1
                val sourceTeam=sourceById["team-$teamId"]!!
                sourceTeam.addProperty("name","Template Team $teamId")
                sourceTeam.addProperty("shortName","T$teamId")
                repeat(2) { driverIndex ->
                    val sourceDriver=sourceById["driver-$teamId-$driverIndex"]!!
                    sourceDriver.getAsJsonObject("m_PersonDetails").addProperty("name","Template Driver $teamId-$driverIndex")
                    sourceDriver.getAsJsonObject("m_PersonDetails").getAsJsonObject("nationality").addProperty("mNationalityID",90+teamId+driverIndex)
                    sourceDriver.getAsJsonObject("m_Stats").getAsJsonArray("m_Stats")[0].asJsonObject.addProperty("m_Stat",99)
                }
            }
        }

        assertTrue(applyOfficialF1Preset2026(target,template))
        val byId=collectObjectsWithId(target).associate { it.second.get("\$id").asString to it.second }
        assertEquals("Original Team 1",byId["team-1"]!!.get("name").asString)
        assertEquals("Original",byId["team-11"]!!.get("shortName").asString)
        assertEquals("Original Team 21",byId["team-21"]!!.get("name").asString)
        assertEquals("Original Team 31",byId["team-31"]!!.get("name").asString)
        assertEquals("Template Driver 1-0",byId["driver-1-0"]!!.getAsJsonObject("m_PersonDetails").get("name").asString)
        assertEquals(91,byId["driver-1-0"]!!.getAsJsonObject("m_PersonDetails").getAsJsonObject("nationality").get("mNationalityID").asInt)
        assertEquals(101,byId["driver-10-1"]!!.getAsJsonObject("m_PersonDetails").getAsJsonObject("nationality").get("mNationalityID").asInt)
        assertEquals(99,byId["driver-1-0"]!!.getAsJsonObject("m_Stats").getAsJsonArray("m_Stats")[0].asJsonObject.get("m_Stat").asInt)
        assertEquals("team-1",byId["driver-1-0"]!!.getAsJsonObject("contract").getAsJsonObject("m_Team").get("\$ref").asString)
        assertEquals("driver-1-0",byId["driver-1-0"]!!.getAsJsonObject("improvement").getAsJsonObject("m_Driver").get("\$ref").asString)
        assertEquals(40,(0..3).sumOf { division ->
            byId["champ-$division"]!!.getAsJsonArray("m_TeamIDs").size()
        })
    }

    @Test
    fun supplierUnlockResolvesReferencesAndSkipsMalformedEntries() {
        val data=JsonObject().apply {
            add("supplierNetworkManager",JsonObject().apply {
                add("m_NodeDictionary",JsonObject().apply {
                    add("root",JsonObject().apply {
                        addProperty("\$id","supplier-root")
                        addProperty("m_IsOwned",false)
                        addProperty("m_HasOwnedBeenRevealed",false)
                        add("m_Nodes",JsonArray().apply {
                            add(JsonObject().apply { addProperty("\$ref","company-1") })
                            add(JsonNull.INSTANCE)
                        })
                    })
                    add("alias",JsonObject().apply { addProperty("\$ref","supplier-root") })
                    add("malformed",JsonPrimitive("unexpected-entry"))
                })
            })
            add("entities",JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("\$id","company-1")
                    addProperty("m_IsOwned",false)
                    addProperty("m_HasOwnedBeenRevealed",false)
                    add("m_Nodes",JsonArray())
                })
            })
        }

        assertEquals(2,unlockSupplierNetwork(data))
        val root=data.getAsJsonObject("supplierNetworkManager")
            .getAsJsonObject("m_NodeDictionary").getAsJsonObject("root")
        val company=data.getAsJsonArray("entities")[0].asJsonObject
        assertTrue(root.get("m_IsOwned").asBoolean)
        assertTrue(root.get("m_HasOwnedBeenRevealed").asBoolean)
        assertTrue(company.get("m_IsOwned").asBoolean)
        assertTrue(company.get("m_HasOwnedBeenRevealed").asBoolean)
        // Idempotent unlock reports the number of recognized nodes, not only changed flags.
        assertEquals(2,unlockSupplierNetwork(data))
    }

    @Test
    fun supplierUnlockRejectsMissingManagerWithoutThrowing() {
        assertEquals(0,unlockSupplierNetwork(JsonObject()))
    }

    @Test
    fun exportRestoresOriginalInlineChampionshipPayloadInsteadOfLeavingReferenceStub() {
        val originalChampionship=JsonObject().apply {
            addProperty("\$id","815")
            addProperty("m_ID",3)
            add("m_TeamIDs",JsonArray().apply { add(63); add(64) })
            add("rules",JsonObject().apply { addProperty("raceDuration",2700.0) })
        }
        val originalData=JsonObject().apply {
            add("entityManager",JsonObject().apply {
                add("m_Entities",JsonArray().apply {
                    add(JsonObject().apply {
                        add("contract",JsonObject().apply {
                            add("m_Team",JsonObject().apply { add("m_Championship",originalChampionship) })
                        })
                    })
                })
            })
        }
        val editedData=originalData.deepCopy().asJsonObject
        editedData.getAsJsonObject("entityManager").getAsJsonArray("m_Entities")[0]
            .asJsonObject.getAsJsonObject("contract").getAsJsonObject("m_Team")
            .add("m_Championship",JsonObject().apply { addProperty("\$ref","815") })
        val doc=Mm4SaveCodec.Document(
            JsonObject(),
            editedData,
            "inline-ref-test.msv",
            originalDataSnapshot=originalData
        )

        val prepared=Mm4SaveCodec.prepareForExport(doc)
        val restored=prepared.data.getAsJsonObject("entityManager").getAsJsonArray("m_Entities")[0]
            .asJsonObject.getAsJsonObject("contract").getAsJsonObject("m_Team")
            .getAsJsonObject("m_Championship")
        assertEquals("815",restored.get("\$id").asString)
        assertEquals(3,restored.get("m_ID").asInt)
        assertEquals(2,restored.getAsJsonArray("m_TeamIDs").size())
        assertTrue(restored.has("rules"))
    }

    @Test
    fun supplierUnlockHandlesRealEmptyDictionaryWithoutClaimingSuccess() {
        val data=JsonObject().apply {
            add("supplierNetworkManager",JsonObject().apply {
                add("m_NodeDictionary",JsonObject())
                add("m_Manufacturers",JsonArray())
                add("m_Companies",JsonArray())
                add("m_TargetNodes",JsonArray().apply {
                    add(JsonNull.INSTANCE); add(JsonNull.INSTANCE); add(JsonNull.INSTANCE)
                })
                add("m_SupplierUnlocked",JsonArray().apply {
                    repeat(3) { add(JsonObject().apply { addProperty("Key",it); addProperty("Value",0) }) }
                })
            })
        }
        assertEquals(0,unlockSupplierNetwork(data))
        assertEquals(0,data.getAsJsonObject("supplierNetworkManager")
            .getAsJsonObject("m_NodeDictionary").size())
    }

    @Test
    fun influenceBalanceEditorHasTwoBillionLimit() {
        val bounds=editorBounds(Mm4SaveCodec.Document(JsonObject(),JsonObject(),"influence.msv"),"Game Data",listOf("influenceManager","m_Influence"))
        assertEquals("0",bounds.first)
        assertEquals("2000000000",bounds.second)
    }

    @Test
    fun financeBalanceEditorHasTwoBillionPoundLimit() {
        val bounds = editorBounds(
            Mm4SaveCodec.Document(JsonObject(), JsonObject(), "finance.msv"),
            "Game Data",
            listOf("financeManager", "m_Balance")
        )
        assertEquals("0", bounds.first)
        assertEquals("2000000000", bounds.second)
    }


}
