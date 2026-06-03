"""Response templates and example data for the MTD module."""

from copy import deepcopy

from config import DEFAULT_COHORT_TOXICITY_ACTION, DEFAULT_INDIVIDUAL_ENDPOINT_ACTION, DEFAULT_REDUCTION_DRAFT, DEFAULT_TOLERABILITY_DEFINITION


def empty_response() -> dict:
    """Return a fresh session-only response structure."""
    return {
        "study_title": "",
        "completed_by": "",
        "immediate_decision": "",
        "immediate_decision_other": "",
        "study_type": "",
        "downstream_study": "",
        "downstream_study_other": "",
        "test_article_types": [],
        "candidate_count": "",
        "candidate_prioritisation": "",
        "test_article_details": {},
        "species": "",
        "strain": "",
        "sex": "",
        "sex_justification": "",
        "animal_status": "",
        "downstream_model": "",
        "model_match": "",
        "model_match_justification": "",
        "adc_target_relevance": "Not applicable",
        "route": "",
        "route_same_downstream": "",
        "downstream_schedule": "",
        "downstream_schedule_other": "",
        "mtd_schedule": "",
        "mtd_schedule_other": "",
        "schedule_alignment": "",
        "schedule_alignment_justification": "",
        "prior_tolerability": "",
        "dose_levels": "",
        "starting_dose_evidence": "",
        "starting_dose_evidence_text": "",
        "intended_outcome": "",
        "tolerability_definition": DEFAULT_TOLERABILITY_DEFINITION,
        "not_tolerated_findings": [],
        "not_tolerated_other": "",
        "individual_endpoint_action": DEFAULT_INDIVIDUAL_ENDPOINT_ACTION,
        "cohort_toxicity_action": DEFAULT_COHORT_TOXICITY_ACTION,
        "observation_period": "",
        "anticipated_adverse_effects": "",
        "monitoring_parameters": [],
        "monitoring_other": "",
        "monitoring_routine": "",
        "monitoring_immediate": "",
        "monitoring_toxicity_window": "",
        "monitoring_after_signs": "",
        "bodyweight_thresholds": "",
        "clinical_scoring": "",
        "supportive_care": [],
        "supportive_care_other": "",
        "decision_maker": "",
        "study_design": "",
        "dose_levels_count": "unknown",
        "animals_per_dose": "unknown",
        "vehicle_control": "",
        "vehicle_control_justification": "",
        "additional_controls": [],
        "additional_controls_other": "",
        "conditional_testing": "",
        "conditional_testing_explanation": "",
        "animal_number_minimisation": DEFAULT_REDUCTION_DRAFT,
        "non_animal_information": [],
        "non_animal_information_other": "",
        "replacement_rationale": "",
        "reduction_measures": DEFAULT_REDUCTION_DRAFT,
        "refinement_measures": "",
    }


def default_article_detail(category: str) -> dict:
    """Return the per-test-article detail fields used by the questionnaire."""
    return {
        "category": category,
        "name": "",
        "development_code": "",
        "material_available": "",
        "characterised": "",
        "formulation": "",
        "prior_in_vitro": "",
        "prior_in_vivo": "",
        "references": "",
        "intended_route": "",
        "free_payload_purpose": "",
        "free_payload_later_route_same": "",
        "free_payload_dose_justification": "",
        "prodrug_parent_payload": "",
        "prodrug_activation": "",
        "prodrug_formulation_complete": "",
        "prodrug_intended_use": "",
        "payload_equivalent_required": "",
        "adc_antibody": "",
        "adc_target_antigen": "",
        "adc_model_target_relevant": "",
        "adc_payload": "",
        "adc_linker": "",
        "adc_dar_known": "",
        "adc_dar_value": "",
        "adc_free_payload_assessed": "",
        "adc_purity_characterised": "",
        "adc_dose_expression": "",
        "adc_unconjugated_control": "",
        "adc_free_payload_comparator": "",
    }


def ensure_article_details(response: dict) -> None:
    """Keep article detail entries aligned with selected article categories."""
    details = response.setdefault("test_article_details", {})
    for category in response.get("test_article_types", []):
        details.setdefault(category, default_article_detail(category))
    for category in list(details):
        if category not in response.get("test_article_types", []):
            details.pop(category)


def worked_adc_incomplete_response() -> dict:
    """Example scenario requested in the build brief."""
    response = empty_response()
    response.update(
        {
            "study_title": "Exploratory MTD study for exatecan ADC candidate",
            "immediate_decision": "Identify a tolerated dose for a later efficacy study",
            "study_type": "Maximum tolerated dose study",
            "downstream_study": "Tumour efficacy study",
            "test_article_types": ["Exatecan-based ADC"],
            "candidate_count": "One lead candidate only",
            "candidate_prioritisation": "Not applicable",
            "species": "Mouse",
            "strain": "",
            "sex": "Female only",
            "sex_justification": "",
            "animal_status": "Tumour-bearing",
            "downstream_model": "Tumour model to be confirmed",
            "model_match": "Unsure",
            "adc_target_relevance": "Unknown",
            "route": "Intravenous",
            "route_same_downstream": "Yes",
            "downstream_schedule": "Once weekly dosing",
            "mtd_schedule": "Not yet decided",
            "schedule_alignment": "Unknown",
            "prior_tolerability": "No",
            "dose_levels": "",
            "starting_dose_evidence": "No evidence entered yet",
            "starting_dose_evidence_text": "",
            "intended_outcome": "Recommended tolerated dose for later efficacy studies",
            "observation_period": "",
            "monitoring_routine": "Daily during acclimatisation and baseline period",
            "monitoring_immediate": "Immediately after dosing",
            "monitoring_toxicity_window": "To be confirmed",
            "monitoring_after_signs": "Increased monitoring after adverse signs",
            "clinical_scoring": "To be confirmed",
            "decision_maker": "Principal investigator and veterinarian",
        }
    )
    response["test_article_details"] = {
        "Exatecan-based ADC": {
            **default_article_detail("Exatecan-based ADC"),
            "name": "Lead exatecan ADC",
            "material_available": "Yes",
            "characterised": "Pending",
            "formulation": "To be confirmed",
            "adc_payload": "Exatecan-related",
            "adc_dar_known": "Pending",
            "adc_linker": "",
            "adc_dose_expression": "Not yet decided",
            "adc_model_target_relevant": "Unknown",
        }
    }
    return response


def completed_example_response() -> dict:
    """A minimal complete-enough example for report generation tests."""
    response = deepcopy(worked_adc_incomplete_response())
    response.update(
        {
            "strain": "BALB/c nude",
            "sex_justification": "Female mice are proposed to align with the planned efficacy model.",
            "downstream_model": "Subcutaneous xenograft model planned for the later efficacy study",
            "model_match": "Yes",
            "adc_target_relevance": "Yes",
            "mtd_schedule": "Once weekly dosing",
            "schedule_alignment": "Yes",
            "prior_tolerability": "Partially",
            "dose_levels": "Investigator-supplied range to be confirmed in protocol draft",
            "starting_dose_evidence": "Closely related compound",
            "starting_dose_evidence_text": "Internal tolerability observations from a related ADC will be reviewed.",
            "observation_period": "14 days after the final dose",
            "anticipated_adverse_effects": "Potential gastrointestinal toxicity, weight loss, and reduced activity will be monitored.",
            "monitoring_parameters": ["Bodyweight", "Activity/behaviour", "Injection site"],
            "bodyweight_thresholds": "Institutional thresholds to be confirmed in the protocol.",
            "clinical_scoring": "Institutional clinical monitoring sheet and humane endpoint criteria.",
            "supportive_care": ["Hydrogel or hydration support", "Veterinary review"],
            "study_design": "Sequential dose escalation using small cohorts",
            "dose_levels_count": "3",
            "animals_per_dose": "3",
            "vehicle_control": "Unsure",
            "vehicle_control_justification": "To be confirmed based on formulation and study design.",
            "conditional_testing": "Yes",
            "conditional_testing_explanation": "Later arms will proceed only if the lead candidate is tolerated.",
            "non_animal_information": ["ADC conjugation/DAR data", "Stability data"],
            "replacement_rationale": "Non-animal characterisation has been used, but in vivo tolerability data are required before later efficacy work.",
            "refinement_measures": "Monitoring, humane endpoints, supportive care, and dose-escalation stopping rules will be used.",
        }
    )
    response["test_article_details"]["Exatecan-based ADC"].update(
        {
            "characterised": "Yes",
            "adc_dar_known": "Yes",
            "adc_dar_value": "To be entered from analytical data",
            "adc_linker": "Cleavable linker description to be confirmed",
            "adc_dose_expression": "mg/kg ADC",
            "adc_free_payload_assessed": "Yes",
            "adc_purity_characterised": "Yes",
        }
    )
    return response
