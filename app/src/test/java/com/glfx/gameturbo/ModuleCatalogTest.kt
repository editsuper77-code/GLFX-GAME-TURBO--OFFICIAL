package com.glfx.gameturbo

import com.glfx.gameturbo.core.modules.Category
import com.glfx.gameturbo.core.modules.ModuleCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleCatalogTest {
    @Test fun idsAreUnique() = assertEquals(ModuleCatalog.all.size, ModuleCatalog.all.map { it.id }.toSet().size)
    @Test fun namesAreUnique() = assertEquals(ModuleCatalog.all.size, ModuleCatalog.all.map { it.name }.toSet().size)
    @Test fun noCategoryExceedsItsTarget() = Category.values().forEach { assertTrue(it.name, ModuleCatalog.count(it) <= it.target) }
    @Test fun everyModuleIsDescribed() = ModuleCatalog.all.forEach {
        assertTrue(it.id, it.description.isNotBlank() && it.implementation.isNotBlank())
    }
}
