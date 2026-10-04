package com.yagay.ysuite.ui

class YSuiteFeatureRegistry(
    registrations: List<YSuiteFeatureUiRegistration>,
) {
    val features: List<YSuiteFeatureUiRegistration> =
        registrations.sortedWith(
            compareBy(
                { it.contract.descriptor.order },
                { it.contract.descriptor.id },
            ),
        )

    init {
        val duplicateIds = registrations
            .groupBy { it.contract.descriptor.id }
            .filterValues { it.size > 1 }
            .keys

        require(duplicateIds.isEmpty()) {
            "Duplicate feature ids: ${duplicateIds.sorted().joinToString()}"
        }

        val duplicateRoutes = registrations
            .flatMap { registration ->
                registration.contract.routes.map { route ->
                    route.value to registration.contract.descriptor.id
                }
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .filterValues { owners -> owners.distinct().size > 1 }

        require(duplicateRoutes.isEmpty()) {
            "Routes owned by multiple features: " +
                duplicateRoutes.entries
                    .sortedBy { it.key }
                    .joinToString { entry ->
                        "${entry.key}=[${entry.value.distinct().sorted().joinToString()}]"
                    }
        }
    }

    fun findById(id: String): YSuiteFeatureUiRegistration? =
        features.firstOrNull { it.contract.descriptor.id == id }

    fun findByRoute(route: String): YSuiteFeatureUiRegistration? =
        features.firstOrNull { registration ->
            registration.contract.routes.any { it.value == route }
        }
}
