package com.lazyapps.steparena.activity

data class ProfileValidationResult(
    val profile: UserBodyProfile?,
    val heightError: Boolean = false,
    val weightError: Boolean = false,
    val stepLengthError: Boolean = false,
) {
    val isValid: Boolean get() = profile != null
}

fun interface UserBodyProfileValidator {
    fun validate(height: String, weight: String, stepLengthCm: String, automatic: Boolean): ProfileValidationResult
}

class DefaultUserBodyProfileValidator : UserBodyProfileValidator {
    override fun validate(
        height: String,
        weight: String,
        stepLengthCm: String,
        automatic: Boolean,
    ): ProfileValidationResult {
        val parsedHeight = height.parseLocalized()
        val parsedWeight = weight.parseLocalized()
        val parsedStep = stepLengthCm.parseLocalized()
        val heightError = parsedHeight.invalidWhenPresent(height, 100.0..250.0)
        val weightError = parsedWeight.invalidWhenPresent(weight, 25.0..300.0)
        val stepError = parsedStep.invalidWhenPresent(stepLengthCm, 20.0..200.0)
        if (heightError || weightError || stepError) {
            return ProfileValidationResult(null, heightError, weightError, stepError)
        }
        return ProfileValidationResult(
            UserBodyProfile(parsedHeight, parsedWeight, parsedStep?.div(100), automatic),
        )
    }

    private fun String.parseLocalized(): Double? =
        trim().takeIf(String::isNotEmpty)?.replace(',', '.')?.toDoubleOrNull()

    private fun Double?.invalidWhenPresent(raw: String, range: ClosedFloatingPointRange<Double>) =
        raw.isNotBlank() && (this == null || !isFinite() || this !in range)
}
