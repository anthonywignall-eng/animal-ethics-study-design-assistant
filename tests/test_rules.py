from data_model import worked_adc_incomplete_response
from rules import (
    ADC_INCOMPLETE_WARNING,
    MULTIPLE_CANDIDATE_WARNING,
    REPEAT_DOSE_SINGLE_MTD_WARNING,
    evaluate_rules,
)


def test_adc_incomplete_rule_triggers():
    response = worked_adc_incomplete_response()

    result = evaluate_rules(response)

    assert ADC_INCOMPLETE_WARNING in result.warnings
    assert any("DAR" in item for item in result.all_missing)
    assert any("linker" in item.lower() for item in result.all_missing)
    assert any("dose-expression" in item.lower() for item in result.all_missing)


def test_repeat_downstream_with_single_dose_mtd_triggers_warning():
    response = worked_adc_incomplete_response()
    response["downstream_study"] = "Repeat-dose toxicity study"
    response["downstream_schedule"] = "Multiple treatment cycles"
    response["mtd_schedule"] = "Single dose"

    result = evaluate_rules(response)

    assert REPEAT_DOSE_SINGLE_MTD_WARNING in result.warnings


def test_multiple_candidates_without_prioritisation_triggers_warning():
    response = worked_adc_incomplete_response()
    response["candidate_count"] = "More than two candidates"
    response["candidate_prioritisation"] = "No"

    result = evaluate_rules(response)

    assert MULTIPLE_CANDIDATE_WARNING in result.warnings
    assert result.readiness == "Study aim or comparison strategy requires clarification before design can be finalised"


def test_worked_adc_example_classified_incomplete_and_flags_expected_items():
    response = worked_adc_incomplete_response()

    result = evaluate_rules(response)

    assert result.readiness == "Design partially complete - critical information required"
    assert ADC_INCOMPLETE_WARNING in result.warnings
    assert any("MTD dosing schedule" in item for item in result.all_missing)
    assert any("Single-sex" in item for item in result.all_missing)
    assert any("Starting-dose" in item for item in result.all_missing)
    assert any("staged escalation" in item for item in result.considerations)
