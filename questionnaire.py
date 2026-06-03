"""Streamlit questionnaire UI for the MTD module.

The workflow is intentionally short: it collects the core design drivers needed to
recommend a pathway, then keeps deeper protocol detail in optional expanders.
"""

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
        st.warning("Information still needed or requiring confirmation:")
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


def _set_default(response: dict, key: str, value: str) -> None:
    if not response.get(key):
        response[key] = value


def render_core_project_stage(response: dict) -> None:
    st.caption("Answer these core questions first. The app can recommend a pathway from these plus the later design-driver stage.")
    _text(response, "study_title", "Working study title")
    _select(response, "immediate_decision", "What decision must this study support?", IMMEDIATE_DECISIONS)
    if response.get("immediate_decision") == "Other":
        _textarea(response, "immediate_decision_other", "Other decision - please explain")
    _select(response, "downstream_study", "What downstream in vivo study is this meant to support?", DOWNSTREAM_STUDIES)
    if response.get("downstream_study") == "Other":
        _textarea(response, "downstream_study_other", "Other downstream study - please describe")
    _multiselect(response, "test_article_types", "What type of test article is proposed?", TEST_ARTICLE_TYPES)
    ensure_article_details(response)
    _select(response, "candidate_count", "How many candidates need animal tolerability testing?", CANDIDATE_COUNTS)
    _select(
        response,
        "candidate_prioritisation",
        "Can candidates be prioritised with non-animal or existing data before animal use?",
        ["Yes", "No", "Unsure", "Not applicable"],
    )
    _multiselect(response, "non_animal_information", "What non-animal or existing information has already been used?", NON_ANIMAL_INFO)
    if "Other" in response.get("non_animal_information", []):
        _textarea(response, "non_animal_information_other", "Other non-animal information")

    with st.expander("Optional administrative detail"):
        _text(response, "completed_by", "Who is completing this questionnaire? Optional and session-only.")
        _select(response, "study_type", "Proposed study type", STUDY_TYPES)


def render_article_readiness_stage(response: dict) -> None:
    if not response.get("test_article_types"):
        st.info("Select one or more test article types in Stage 1 before completing readiness details.")
        return

    ensure_article_details(response)
    for category in response.get("test_article_types", []):
        detail = response["test_article_details"][category]
        prefix = f"article_{_slug(category)}"
        with st.expander(category, expanded=True):
            _detail_text(detail, "name", "Compound or construct name", f"{prefix}_name")
            _detail_select(detail, "material_available", "Is material available for dosing?", MATERIAL_STATUS_OPTIONS, f"{prefix}_available")
            _detail_select(detail, "characterised", "Is it sufficiently characterised for in vivo use?", MATERIAL_STATUS_OPTIONS, f"{prefix}_characterised")
            _detail_textarea(detail, "formulation", "Known formulation or vehicle", f"{prefix}_formulation")

            if category in ADC_TYPES:
                st.markdown("**Essential ADC readiness checks**")
                _detail_select(detail, "adc_payload", "Payload", ["Exatecan-related", "SN-38-related", "Other", "Unknown"], f"{prefix}_payload")
                _detail_text(detail, "adc_target_antigen", "Target antigen, if known", f"{prefix}_target")
                _detail_select(detail, "adc_model_target_relevant", "Is target relevance known for the proposed model?", YES_NO_UNKNOWN, f"{prefix}_model_relevant")
                _detail_textarea(detail, "adc_linker", "Linker type or linker description", f"{prefix}_linker")
                _detail_select(detail, "adc_dar_known", "Is DAR known?", ["Yes", "No", "Pending"], f"{prefix}_dar_known")
                if detail.get("adc_dar_known") == "Yes":
                    _detail_text(detail, "adc_dar_value", "Enter DAR", f"{prefix}_dar_value")
                _detail_select(
                    detail,
                    "adc_dose_expression",
                    "How should dose be expressed?",
                    ["mg/kg ADC", "mg/kg antibody", "payload-equivalent dose", "Not yet decided"],
                    f"{prefix}_dose_expression",
                )
                with st.expander("Optional ADC detail"):
                    _detail_text(detail, "adc_antibody", "Antibody or targeting component", f"{prefix}_antibody")
                    _detail_select(detail, "adc_free_payload_assessed", "Has free payload content been assessed?", YES_NO_PENDING, f"{prefix}_free_payload_assessed")
                    _detail_select(detail, "adc_purity_characterised", "Has purity/aggregation/stability been characterised?", YES_NO_PENDING, f"{prefix}_purity")
                    _detail_select(detail, "adc_unconjugated_control", "Is an unconjugated antibody control expected later?", YES_NO_UNSURE, f"{prefix}_unconjugated")
                    _detail_select(detail, "adc_free_payload_comparator", "Is a free payload comparator expected later?", YES_NO_UNSURE, f"{prefix}_free_payload_comparator")

            if category in PRODRUG_TYPES:
                st.markdown("**Essential prodrug readiness checks**")
                _detail_select(detail, "prodrug_parent_payload", "Parent active payload", ["Exatecan", "SN-38", "Other"], f"{prefix}_parent")
                _detail_textarea(detail, "prodrug_activation", "What is known about activation/release?", f"{prefix}_activation")
                _detail_select(detail, "prodrug_formulation_complete", "Is formulation suitable for dosing?", YES_NO_UNSURE, f"{prefix}_prodrug_formulation_complete")
                _detail_select(detail, "payload_equivalent_required", "Is payload-equivalent comparison required?", YES_NO_UNSURE, f"{prefix}_payload_equiv")
                with st.expander("Optional prodrug detail"):
                    _detail_select(
                        detail,
                        "prodrug_intended_use",
                        "Intended use",
                        ["Direct therapeutic use", "ADC development pathway", "Both", "Not yet decided"],
                        f"{prefix}_prodrug_use",
                    )

            if category in FREE_PAYLOAD_TYPES:
                st.markdown("**Essential free-payload checks**")
                _detail_select(
                    detail,
                    "free_payload_purpose",
                    "Standalone candidate or comparator?",
                    ["Standalone therapeutic candidate", "Payload comparator", "Both", "Not yet decided"],
                    f"{prefix}_payload_purpose",
                )
                _detail_textarea(detail, "free_payload_dose_justification", "Published/internal justification for starting range, if known", f"{prefix}_payload_justification")

            with st.expander("Optional evidence and reference detail"):
                _detail_text(detail, "development_code", "Development code, if available", f"{prefix}_code")
                _detail_textarea(detail, "prior_in_vitro", "Prior in vitro data", f"{prefix}_invitro")
                _detail_textarea(detail, "prior_in_vivo", "Prior in vivo dose or tolerability information", f"{prefix}_invivo")
                _detail_textarea(detail, "references", "Relevant published/internal evidence", f"{prefix}_references")
                _detail_text(detail, "intended_route", "Intended route, if known", f"{prefix}_route")


def render_design_drivers_stage(response: dict) -> None:
    left, right = st.columns(2)
    with left:
        _select(response, "species", "Species", SPECIES_OPTIONS)
        _text(response, "strain", "Strain")
        _select(response, "sex", "Sex", SEX_OPTIONS)
        if response.get("sex") in {"Female only", "Male only"}:
            _textarea(response, "sex_justification", "Why is a single sex appropriate?")
        _select(response, "animal_status", "Animal model type", MODEL_OPTIONS)
        _textarea(response, "downstream_model", "Downstream model or tumour model, if known")
    with right:
        _select(response, "route", "Route of administration", ROUTE_OPTIONS)
        _select(response, "downstream_schedule", "Downstream dosing schedule", SCHEDULE_OPTIONS)
        if response.get("downstream_schedule") == "Other":
            _textarea(response, "downstream_schedule_other", "Other downstream dosing schedule")
        _select(response, "mtd_schedule", "Proposed MTD/tolerability schedule", SCHEDULE_OPTIONS)
        if response.get("mtd_schedule") == "Other":
            _textarea(response, "mtd_schedule_other", "Other MTD dosing schedule")
        _select(response, "prior_tolerability", "Prior in vivo tolerability data in relevant species/route/schedule?", PRIOR_TOLERABILITY_OPTIONS)
        _select(response, "starting_dose_evidence", "Evidence for starting dose or dose range", DOSE_EVIDENCE_OPTIONS)
        _textarea(response, "starting_dose_evidence_text", "Evidence details or explanation")

    with st.expander("Optional alignment and dose-range detail"):
        _select(response, "model_match", "Should the MTD model match the later study model?", ["Yes", "No, with justification", "Unsure"])
        if response.get("model_match") == "No, with justification":
            _textarea(response, "model_match_justification", "Justification for using a different MTD model")
        _select(response, "route_same_downstream", "Is this the same route intended downstream?", YES_NO_UNKNOWN)
        _select(response, "schedule_alignment", "Does the MTD schedule align with the likely downstream schedule?", ["Yes", "No, with justification", "Unknown"])
        if response.get("schedule_alignment") == "No, with justification":
            _textarea(response, "schedule_alignment_justification", "Justification for different MTD and downstream schedules")
        _textarea(response, "dose_levels", "Proposed dose levels or range, if already known. The tool will not recommend numerical doses.")
        if set(response.get("test_article_types", [])) & ADC_TYPES:
            _select(response, "adc_target_relevance", "For ADCs, is target expression/model biology relevant to tolerability interpretation?", ["Yes", "No", "Unknown", "Not applicable"])


def render_welfare_use_stage(response: dict) -> None:
    _apply_fast_defaults(response)
    left, right = st.columns(2)
    with left:
        _select(response, "intended_outcome", "Intended study outcome", OUTCOME_OPTIONS)
        _text(response, "observation_period", "Observation period after final dose")
        _textarea(response, "anticipated_adverse_effects", "Anticipated adverse effects or toxicity concerns")
        _multiselect(response, "monitoring_parameters", "Core monitoring parameters", MONITORING_PARAMETERS)
        _textarea(response, "clinical_scoring", "Humane endpoint/intervention criteria or scoring system", height=100)
    with right:
        _multiselect(response, "supportive_care", "Supportive care or refinement options", SUPPORTIVE_CARE_OPTIONS)
        _text(response, "decision_maker", "Who can decide intervention, euthanasia or escalation stopping?")
        _select(response, "study_design", "Likely design structure", STUDY_DESIGN_OPTIONS)
        _text(response, "dose_levels_count", "Anticipated dose levels. Number or 'unknown'.")
        _text(response, "animals_per_dose", "Animals per dose level. Number or 'unknown'.")

    with st.expander("Optional monitoring schedule and 3Rs wording"):
        _text(response, "monitoring_routine", "Routine monitoring")
        _text(response, "monitoring_immediate", "Immediately after dosing")
        _text(response, "monitoring_toxicity_window", "During expected toxicity window")
        _text(response, "monitoring_after_signs", "After adverse signs occur")
        _textarea(response, "bodyweight_thresholds", "Bodyweight-loss thresholds, if known")
        _textarea(response, "tolerability_definition", "MTD/tolerability definition", height=100)
        _textarea(response, "individual_endpoint_action", "Action if an individual animal reaches a humane endpoint", height=90)
        _textarea(response, "cohort_toxicity_action", "Action if more than one animal in a cohort has unacceptable toxicity", height=90)
        _select(response, "vehicle_control", "Is a vehicle control required?", YES_NO_UNSURE)
        _textarea(response, "vehicle_control_justification", "Vehicle-control justification")
        _multiselect(response, "additional_controls", "Additional controls proposed", ADDITIONAL_CONTROLS)
        _select(response, "conditional_testing", "Can later testing be conditional on lead-candidate performance?", YES_NO_UNSURE)
        _textarea(response, "conditional_testing_explanation", "Conditional testing explanation")
        _textarea(response, "animal_number_minimisation", "How animal numbers have been minimised", height=100)
        _textarea(response, "replacement_rationale", "Replacement rationale", height=100)
        _textarea(response, "reduction_measures", "Reduction measures", height=100)
        _textarea(response, "refinement_measures", "Refinement measures", height=100)


def _apply_fast_defaults(response: dict) -> None:
    _set_default(response, "monitoring_routine", "Routine monitoring before and after dosing according to facility SOP; frequency to be confirmed in the final protocol.")
    _set_default(response, "monitoring_immediate", "Animals will be observed immediately after dosing according to route-specific SOP and expected acute-risk window.")
    _set_default(response, "monitoring_toxicity_window", "Monitoring during the expected toxicity window will be increased and final timing requires investigator/veterinary confirmation.")
    _set_default(response, "monitoring_after_signs", "Animals showing adverse clinical signs will receive increased monitoring and welfare review.")
    _set_default(response, "clinical_scoring", "Institutional clinical scoring and humane endpoint/intervention criteria to be confirmed before submission.")
    _set_default(response, "decision_maker", "Principal investigator, veterinarian or approved delegate to be confirmed.")
    _set_default(response, "observation_period", "To be confirmed based on dosing schedule and expected toxicity window.")
    _set_default(response, "animal_number_minimisation", DEFAULT_REDUCTION_DRAFT)
    _set_default(response, "reduction_measures", response.get("animal_number_minimisation") or DEFAULT_REDUCTION_DRAFT)
    _set_default(response, "replacement_rationale", "Draft prompt: Existing non-animal and prior evidence should be used for candidate selection; in vivo tolerability is proposed only where needed to support downstream animal work.")
    _set_default(response, "refinement_measures", "Draft prompt: Refinements include defined monitoring, humane endpoints, cohort review before escalation, supportive care where compatible with interpretation and veterinary review.")


def stage_required_warnings(stage_id: str, response: dict) -> list[str]:
    warnings: list[str] = []
    if stage_id == "core_project":
        if not response.get("immediate_decision"):
            warnings.append("Select the decision this study must support.")
        if not response.get("downstream_study"):
            warnings.append("Select the downstream study or mark it as not yet defined.")
        if not response.get("test_article_types"):
            warnings.append("Select at least one test article category.")
        if not response.get("candidate_count"):
            warnings.append("Candidate comparison strategy requires confirmation.")
    elif stage_id == "article_readiness":
        for category, detail in (response.get("test_article_details") or {}).items():
            if category in ADC_TYPES:
                if not detail.get("adc_linker"):
                    warnings.append(f"{category}: linker description is missing.")
                if detail.get("adc_dar_known") != "Yes":
                    warnings.append(f"{category}: DAR is not confirmed.")
                if detail.get("adc_dose_expression") in {"", "Not yet decided"}:
                    warnings.append(f"{category}: dose-expression basis is not confirmed.")
    elif stage_id == "design_drivers":
        if response.get("sex") in {"Female only", "Male only"} and not response.get("sex_justification"):
            warnings.append("Single-sex animal use requires justification.")
        if not response.get("route") or response.get("route") == "Not yet decided":
            warnings.append("Route requires confirmation.")
        if not response.get("mtd_schedule") or response.get("mtd_schedule") == "Not yet decided":
            warnings.append("MTD/tolerability schedule requires confirmation.")
        if response.get("starting_dose_evidence") in {"", "No evidence entered yet"} and not response.get("starting_dose_evidence_text"):
            warnings.append("Starting-dose or dose-range rationale requires supporting evidence or explanation.")
    elif stage_id == "welfare_use":
        if not response.get("monitoring_parameters"):
            warnings.append("Select core monitoring parameters.")
        if not response.get("study_design"):
            warnings.append("Select the likely design structure, even if it is not yet decided.")
    return warnings


STAGE_RENDERERS = {
    "core_project": render_core_project_stage,
    "article_readiness": render_article_readiness_stage,
    "design_drivers": render_design_drivers_stage,
    "welfare_use": render_welfare_use_stage,
}
