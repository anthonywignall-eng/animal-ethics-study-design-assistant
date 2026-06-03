"""Streamlit questionnaire UI for the MTD module."""

import re

import streamlit as st

from config import (
    ADDITIONAL_CONTROLS,
    ADC_TYPES,
    CANDIDATE_COUNTS,
    DEFAULT_REDUCTION_DRAFT,
    DOSE_EVIDENCE_OPTIONS,
    DOWNSTREAM_STUDIES,
    FREE_PAYLOAD_TYPES,
    IMMEDIATE_DECISIONS,
    MODEL_OPTIONS,
    MONITORING_PARAMETERS,
    NON_ANIMAL_INFO,
    NOT_TOLERATED_FINDINGS,
    OUTCOME_OPTIONS,
    PRIOR_TOLERABILITY_OPTIONS,
    PRODRUG_TYPES,
    ROUTE_OPTIONS,
    SCHEDULE_OPTIONS,
    SEX_OPTIONS,
    SPECIES_OPTIONS,
    STAGES,
    STUDY_DESIGN_OPTIONS,
    STUDY_TYPES,
    SUPPORTIVE_CARE_OPTIONS,
    TEST_ARTICLE_TYPES,
    YES_NO_PENDING,
    YES_NO_UNKNOWN,
    YES_NO_UNSURE,
)
from data_model import ensure_article_details


MATERIAL_STATUS_OPTIONS = ["Yes", "No", "Pending", "Not yet decided"]


def render_stage(stage_index: int, response: dict) -> None:
    stage = STAGES[stage_index]
    st.subheader(f"Stage {stage_index + 1}: {stage['title']}")
    renderer = STAGE_RENDERERS[stage["id"]]
    renderer(response)

    warnings = stage_required_warnings(stage["id"], response)
    if warnings:
        st.warning("Information still needed on this stage:")
        for warning in warnings:
            st.write(f"- {warning}")


def _slug(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", value.lower()).strip("_")


def _text(response: dict, key: str, label: str, help_text: str | None = None) -> None:
    response[key] = st.text_input(label, value=response.get(key, ""), help=help_text, key=key)


def _textarea(response: dict, key: str, label: str, height: int = 90, help_text: str | None = None) -> None:
    response[key] = st.text_area(label, value=response.get(key, ""), height=height, help=help_text, key=key)


def _select(response: dict, key: str, label: str, options: list[str], help_text: str | None = None) -> None:
    values = [""] + options
    current = response.get(key, "")
    index = values.index(current) if current in values else 0
    response[key] = st.selectbox(label, values, index=index, help=help_text, key=key)


def _multiselect(response: dict, key: str, label: str, options: list[str], help_text: str | None = None) -> None:
    current = [value for value in response.get(key, []) if value in options]
    response[key] = st.multiselect(label, options, default=current, help=help_text, key=key)


def _detail_text(detail: dict, key: str, label: str, widget_key: str) -> None:
    detail[key] = st.text_input(label, value=detail.get(key, ""), key=widget_key)


def _detail_textarea(detail: dict, key: str, label: str, widget_key: str, height: int = 80) -> None:
    detail[key] = st.text_area(label, value=detail.get(key, ""), height=height, key=widget_key)


def _detail_select(detail: dict, key: str, label: str, options: list[str], widget_key: str) -> None:
    values = [""] + options
    current = detail.get(key, "")
    index = values.index(current) if current in values else 0
    detail[key] = st.selectbox(label, values, index=index, key=widget_key)


def render_project_stage(response: dict) -> None:
    _text(response, "study_title", "What is the working title of the proposed study?")
    _text(response, "completed_by", "Who is completing this questionnaire? Optional. This is session-only and not saved by the app.")
    _select(response, "immediate_decision", "What immediate decision must this study support?", IMMEDIATE_DECISIONS)
    if response.get("immediate_decision") == "Other":
        _textarea(response, "immediate_decision_other", "Other decision - please explain")
    _select(response, "study_type", "What is the proposed study type?", STUDY_TYPES)
    _select(response, "downstream_study", "What downstream in vivo study is planned after this study?", DOWNSTREAM_STUDIES)
    if response.get("downstream_study") == "Other":
        _textarea(response, "downstream_study_other", "Other downstream study - please describe")


def render_selection_stage(response: dict) -> None:
    _multiselect(response, "test_article_types", "Which type of test article is proposed?", TEST_ARTICLE_TYPES)
    ensure_article_details(response)
    _select(response, "candidate_count", "Is there one lead candidate, or are multiple candidate agents intended to be tested?", CANDIDATE_COUNTS)
    _select(
        response,
        "candidate_prioritisation",
        "Can candidate therapies be prioritised using existing in vitro, formulation, stability, conjugation or efficacy information before animals are used?",
        ["Yes", "No", "Unsure", "Not applicable"],
    )


def render_details_stage(response: dict) -> None:
    if not response.get("test_article_types"):
        st.info("Select one or more test article types in Stage 2 before completing details.")
        return

    ensure_article_details(response)
    for category in response.get("test_article_types", []):
        detail = response["test_article_details"][category]
        prefix = f"article_{_slug(category)}"
        with st.expander(category, expanded=True):
            _detail_text(detail, "name", "Compound or construct name", f"{prefix}_name")
            _detail_text(detail, "development_code", "Development code, if available", f"{prefix}_code")
            _detail_select(detail, "material_available", "Is the material currently available for dosing?", MATERIAL_STATUS_OPTIONS, f"{prefix}_available")
            _detail_select(detail, "characterised", "Is the material sufficiently characterised for in vivo use?", MATERIAL_STATUS_OPTIONS, f"{prefix}_characterised")
            _detail_textarea(detail, "formulation", "Proposed formulation or vehicle, if known", f"{prefix}_formulation")
            _detail_textarea(detail, "prior_in_vitro", "Any prior in vitro data available", f"{prefix}_invitro")
            _detail_textarea(detail, "prior_in_vivo", "Any prior in vivo dose or tolerability information available", f"{prefix}_invivo")
            _detail_textarea(detail, "references", "Citation/reference field for relevant published or internal evidence", f"{prefix}_references")
            _detail_text(detail, "intended_route", "Intended route of administration, if known", f"{prefix}_route")

            if category in FREE_PAYLOAD_TYPES:
                st.markdown("**Free payload-specific questions**")
                _detail_select(
                    detail,
                    "free_payload_purpose",
                    "Is this being tested as a standalone therapeutic candidate or as a payload comparator?",
                    ["Standalone therapeutic candidate", "Payload comparator", "Both", "Not yet decided"],
                    f"{prefix}_payload_purpose",
                )
                _detail_select(detail, "free_payload_later_route_same", "Is the intended later route the same as in this MTD study?", YES_NO_UNKNOWN, f"{prefix}_payload_route_same")
                _detail_textarea(detail, "free_payload_dose_justification", "Is there published or internal dose justification for the proposed starting range?", f"{prefix}_payload_justification")

            if category in PRODRUG_TYPES:
                st.markdown("**Prodrug-specific questions**")
                _detail_select(detail, "prodrug_parent_payload", "What is the parent active payload?", ["Exatecan", "SN-38", "Other"], f"{prefix}_parent")
                _detail_textarea(detail, "prodrug_activation", "What is known about activation/release of the active payload?", f"{prefix}_activation")
                _detail_select(detail, "prodrug_formulation_complete", "Is the prodrug formulation complete and suitable for dosing?", YES_NO_UNSURE, f"{prefix}_prodrug_formulation_complete")
                _detail_select(
                    detail,
                    "prodrug_intended_use",
                    "Is the prodrug intended for direct therapeutic use or as part of an ADC development pathway?",
                    ["Direct therapeutic use", "ADC development pathway", "Both", "Not yet decided"],
                    f"{prefix}_prodrug_use",
                )
                _detail_select(detail, "payload_equivalent_required", "Is a payload-equivalent comparison required?", YES_NO_UNSURE, f"{prefix}_payload_equiv")

            if category in ADC_TYPES:
                st.markdown("**ADC-specific questions**")
                _detail_text(detail, "adc_antibody", "What is the antibody or targeting component?", f"{prefix}_antibody")
                _detail_text(detail, "adc_target_antigen", "What is the target antigen?", f"{prefix}_target")
                _detail_select(detail, "adc_model_target_relevant", "Is the target relevant in the proposed mouse/tumour model?", YES_NO_UNKNOWN, f"{prefix}_model_relevant")
                _detail_select(detail, "adc_payload", "What is the payload?", ["Exatecan-related", "SN-38-related", "Other", "Unknown"], f"{prefix}_payload")
                _detail_textarea(detail, "adc_linker", "What is the linker type or linker description?", f"{prefix}_linker")
                _detail_select(detail, "adc_dar_known", "Is the drug-to-antibody ratio (DAR) known?", ["Yes", "No", "Pending"], f"{prefix}_dar_known")
                if detail.get("adc_dar_known") == "Yes":
                    _detail_text(detail, "adc_dar_value", "Enter DAR", f"{prefix}_dar_value")
                _detail_select(detail, "adc_free_payload_assessed", "Has free payload content been assessed?", YES_NO_PENDING, f"{prefix}_free_payload_assessed")
                _detail_select(detail, "adc_purity_characterised", "Has purity/aggregation/stability been characterised?", YES_NO_PENDING, f"{prefix}_purity")
                _detail_select(
                    detail,
                    "adc_dose_expression",
                    "How will dose be expressed?",
                    ["mg/kg ADC", "mg/kg antibody", "payload-equivalent dose", "Not yet decided"],
                    f"{prefix}_dose_expression",
                )
                _detail_select(detail, "adc_unconjugated_control", "Is an unconjugated antibody control expected in later studies?", YES_NO_UNSURE, f"{prefix}_unconjugated")
                _detail_select(detail, "adc_free_payload_comparator", "Is a free payload comparator expected?", YES_NO_UNSURE, f"{prefix}_free_payload_comparator")


def render_animal_model_stage(response: dict) -> None:
    _select(response, "species", "What species is proposed?", SPECIES_OPTIONS)
    _text(response, "strain", "What strain is proposed?")
    _select(response, "sex", "What sex is proposed?", SEX_OPTIONS)
    if response.get("sex") in {"Female only", "Male only"}:
        _textarea(response, "sex_justification", "Why is this sex appropriate?")
    _select(response, "animal_status", "Will animals be:", MODEL_OPTIONS)
    _textarea(response, "downstream_model", "What animal model will be used in the downstream efficacy or PK study?")
    _select(response, "model_match", "Should the MTD model match the intended later study model?", ["Yes", "No, with justification", "Unsure"])
    if response.get("model_match") == "No, with justification":
        _textarea(response, "model_match_justification", "Justification for using a different MTD model")
    if set(response.get("test_article_types", [])) & ADC_TYPES:
        _select(response, "adc_target_relevance", "If an ADC is being evaluated, is target expression in the animal or tumour model relevant to interpretation of tolerability?", ["Yes", "No", "Unknown", "Not applicable"])


def render_route_schedule_stage(response: dict) -> None:
    _select(response, "route", "What route of administration is intended?", ROUTE_OPTIONS)
    _select(response, "route_same_downstream", "Is this the same route intended for the downstream study?", YES_NO_UNKNOWN)
    _select(response, "downstream_schedule", "What dosing schedule is intended in the downstream study?", SCHEDULE_OPTIONS)
    if response.get("downstream_schedule") == "Other":
        _textarea(response, "downstream_schedule_other", "Other downstream dosing schedule")
    _select(response, "mtd_schedule", "What dosing schedule is proposed for the MTD study?", SCHEDULE_OPTIONS)
    if response.get("mtd_schedule") == "Other":
        _textarea(response, "mtd_schedule_other", "Other MTD dosing schedule")
    _select(response, "schedule_alignment", "Is the proposed MTD schedule intended to replicate the likely efficacy-study schedule?", ["Yes", "No, with justification", "Unknown"])
    if response.get("schedule_alignment") == "No, with justification":
        _textarea(response, "schedule_alignment_justification", "Justification for different MTD and downstream schedules")
    _select(response, "prior_tolerability", "Are prior in vivo tolerability data available in the same species, strain, route and schedule?", PRIOR_TOLERABILITY_OPTIONS)
    _textarea(response, "dose_levels", "Enter any proposed dose levels or dose range, if known. The tool will not recommend numerical doses.")
    _select(response, "starting_dose_evidence", "What evidence supports the proposed starting dose or dose range?", DOSE_EVIDENCE_OPTIONS)
    _textarea(response, "starting_dose_evidence_text", "Evidence details or investigator explanation")


def render_tolerability_stage(response: dict) -> None:
    _select(response, "intended_outcome", "What outcome is intended?", OUTCOME_OPTIONS)
    _textarea(response, "tolerability_definition", "How will MTD or tolerability be defined?", height=120)
    _multiselect(response, "not_tolerated_findings", "What findings would indicate that a dose is not tolerated?", NOT_TOLERATED_FINDINGS)
    if "Other" in response.get("not_tolerated_findings", []):
        _textarea(response, "not_tolerated_other", "Other findings")
    _textarea(response, "individual_endpoint_action", "What happens if an individual animal reaches a humane endpoint?", height=100)
    _textarea(response, "cohort_toxicity_action", "What happens if more than one animal in a cohort demonstrates unacceptable toxicity?", height=100)
    _text(response, "observation_period", "What is the planned observation period after the final dose?")


def render_welfare_stage(response: dict) -> None:
    _textarea(response, "anticipated_adverse_effects", "What adverse effects are anticipated for the selected therapy class?")
    _multiselect(response, "monitoring_parameters", "What parameters will be monitored?", MONITORING_PARAMETERS)
    if "Other" in response.get("monitoring_parameters", []):
        _textarea(response, "monitoring_other", "Other monitoring parameters")
    st.markdown("**Monitoring frequency before and after dosing**")
    _text(response, "monitoring_routine", "Routine monitoring")
    _text(response, "monitoring_immediate", "Immediately after dosing")
    _text(response, "monitoring_toxicity_window", "Monitoring during expected toxicity window")
    _text(response, "monitoring_after_signs", "Monitoring after adverse clinical signs occur")
    _textarea(response, "bodyweight_thresholds", "What predefined bodyweight-loss thresholds are proposed?")
    _textarea(response, "clinical_scoring", "What clinical scoring system or intervention criteria will be used?")
    _multiselect(response, "supportive_care", "What supportive care is considered appropriate and compatible with interpretation of the study?", SUPPORTIVE_CARE_OPTIONS)
    if "Other" in response.get("supportive_care", []):
        _textarea(response, "supportive_care_other", "Other supportive care")
    _text(response, "decision_maker", "Who is authorised to make decisions about intervention, euthanasia or dose-escalation stopping?")


def render_numbers_stage(response: dict) -> None:
    _select(response, "study_design", "Is the study proposed as:", STUDY_DESIGN_OPTIONS)
    _text(response, "dose_levels_count", "How many dose levels are anticipated? Enter a number or 'unknown'.")
    _text(response, "animals_per_dose", "How many animals per dose level are anticipated? Enter a number or 'unknown'.")
    _select(response, "vehicle_control", "Is a vehicle control required?", YES_NO_UNSURE)
    _textarea(response, "vehicle_control_justification", "Vehicle-control justification")
    _multiselect(response, "additional_controls", "Are additional controls proposed?", ADDITIONAL_CONTROLS)
    if "Other" in response.get("additional_controls", []):
        _textarea(response, "additional_controls_other", "Other controls")
    _select(response, "conditional_testing", "Can any later testing be conditional on the performance of the lead candidate?", YES_NO_UNSURE)
    _textarea(response, "conditional_testing_explanation", "Conditional testing explanation")
    if not response.get("animal_number_minimisation"):
        response["animal_number_minimisation"] = DEFAULT_REDUCTION_DRAFT
    _textarea(response, "animal_number_minimisation", "Explain how animal numbers have been minimised.", height=120)


def render_three_rs_stage(response: dict) -> None:
    _multiselect(response, "non_animal_information", "What non-animal information has already been used to prioritise the proposed candidate or dose strategy?", NON_ANIMAL_INFO)
    if "Other" in response.get("non_animal_information", []):
        _textarea(response, "non_animal_information_other", "Other non-animal information")
    if not response.get("replacement_rationale"):
        response["replacement_rationale"] = build_replacement_draft(response)
    if not response.get("reduction_measures"):
        response["reduction_measures"] = response.get("animal_number_minimisation") or DEFAULT_REDUCTION_DRAFT
    if not response.get("refinement_measures"):
        response["refinement_measures"] = build_refinement_draft(response)
    _textarea(response, "replacement_rationale", "Replacement rationale", height=120)
    _textarea(response, "reduction_measures", "Reduction measures", height=120)
    _textarea(response, "refinement_measures", "Refinement measures", height=120)


def build_replacement_draft(response: dict) -> str:
    info = ", ".join(response.get("non_animal_information", [])) or "the non-animal information entered by the investigator"
    downstream = response.get("downstream_study") or "the planned downstream study"
    return (
        f"Draft prompt: {info} should be described as part of candidate selection. "
        f"An in vivo tolerability study is proposed to inform dosing before {downstream}."
    )


def build_refinement_draft(response: dict) -> str:
    monitoring = ", ".join(response.get("monitoring_parameters", [])) or "the proposed monitoring parameters"
    care = ", ".join(response.get("supportive_care", [])) or "appropriate supportive care"
    return (
        f"Draft prompt: Refinement measures include monitoring of {monitoring}, predefined humane endpoints, "
        f"dose-escalation stopping rules and {care}, subject to investigator and veterinary review."
    )


def stage_required_warnings(stage_id: str, response: dict) -> list[str]:
    warnings: list[str] = []
    if stage_id == "project":
        if not response.get("study_title"):
            warnings.append("Working study title is required for the report.")
        if not response.get("study_type") or response.get("study_type") == "Unsure":
            warnings.append("Proposed study type requires confirmation.")
        if not response.get("downstream_study"):
            warnings.append("Downstream study should be selected or marked as not yet defined.")
    elif stage_id == "selection":
        if not response.get("test_article_types"):
            warnings.append("Select at least one test article category.")
        if not response.get("candidate_count"):
            warnings.append("Candidate comparison strategy requires confirmation.")
    elif stage_id == "details":
        for category, detail in (response.get("test_article_details") or {}).items():
            if category in ADC_TYPES:
                if not detail.get("adc_linker"):
                    warnings.append(f"{category}: linker description is missing.")
                if detail.get("adc_dar_known") != "Yes":
                    warnings.append(f"{category}: DAR is not confirmed.")
                if detail.get("adc_dose_expression") in {"", "Not yet decided"}:
                    warnings.append(f"{category}: dose-expression basis is not confirmed.")
    elif stage_id == "animal_model":
        if response.get("sex") in {"Female only", "Male only"} and not response.get("sex_justification"):
            warnings.append("Single-sex animal use requires justification.")
        if not response.get("strain"):
            warnings.append("Animal strain is missing.")
    elif stage_id == "route_schedule":
        if not response.get("mtd_schedule") or response.get("mtd_schedule") == "Not yet decided":
            warnings.append("MTD dosing schedule requires confirmation.")
        if response.get("starting_dose_evidence") in {"", "No evidence entered yet"} and not response.get("starting_dose_evidence_text"):
            warnings.append("Starting-dose or dose-range rationale requires supporting evidence or explanation.")
    elif stage_id == "tolerability":
        if not response.get("tolerability_definition"):
            warnings.append("Tolerability definition is missing.")
        if not response.get("observation_period"):
            warnings.append("Observation period is missing.")
    elif stage_id == "welfare":
        if any(not response.get(key) for key in ["monitoring_routine", "monitoring_immediate", "monitoring_toxicity_window", "monitoring_after_signs"]):
            warnings.append("Monitoring frequency fields require completion.")
        if not response.get("clinical_scoring"):
            warnings.append("Clinical scoring or intervention criteria are missing.")
        if not response.get("decision_maker"):
            warnings.append("Authorised decision-maker is missing.")
    return warnings


STAGE_RENDERERS = {
    "project": render_project_stage,
    "selection": render_selection_stage,
    "details": render_details_stage,
    "animal_model": render_animal_model_stage,
    "route_schedule": render_route_schedule_stage,
    "tolerability": render_tolerability_stage,
    "welfare": render_welfare_stage,
    "numbers": render_numbers_stage,
    "three_rs": render_three_rs_stage,
}
