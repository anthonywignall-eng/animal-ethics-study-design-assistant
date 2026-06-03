"""Rule engine for the MTD module.

The rules are deliberately kept outside the Streamlit app so new study modules can
bring their own rule functions and still share the report-generation workflow.
"""

from dataclasses import dataclass, field

from config import ADC_TYPES, FREE_PAYLOAD_TYPES, PRODRUG_TYPES


ADC_INCOMPLETE_WARNING = (
    "ADC design information is incomplete. Linker description, DAR and dose-expression "
    "basis should be confirmed before an ADC MTD design is finalised or compared with "
    "free payload/prodrug dosing."
)

DOWNSTREAM_UNDEFINED_WARNING = (
    "The downstream use of the tolerability information has not yet been defined. "
    "Consider whether a limited preliminary tolerability study is more appropriate than "
    "a full MTD determination until the progression decision is clarified."
)

MULTIPLE_CANDIDATE_WARNING = (
    "Multiple candidate therapies have been selected for animal tolerability testing. "
    "The investigator should justify why candidates cannot first be prioritised using "
    "existing in vitro, formulation, stability or conjugation data, as a staged approach "
    "may reduce animal use."
)

PAYLOAD_COMPARISON_WARNING = (
    "Direct comparison across free payload, prodrug and ADC classes may be misleading "
    "unless the comparison basis is clearly defined, such as payload-equivalent exposure "
    "or another scientifically justified metric."
)

REPEAT_DOSE_SINGLE_MTD_WARNING = (
    "A single-dose tolerability study alone may not adequately support selection of a "
    "repeated-dose efficacy or toxicity regimen. The investigator should justify whether "
    "repeat-dose tolerability confirmation is required."
)

NO_PRIOR_TOLERABILITY_CONSIDERATION = (
    "A staged escalation design using small cohorts and predefined escalation-stop criteria "
    "should be considered because prior in vivo tolerability information has not been supplied."
)

ADC_TARGET_RELEVANCE_CONSIDERATION = (
    "For an ADC, the relevance of target expression or tumour model biology should be "
    "considered when interpreting tolerability and selecting the later efficacy-study dose."
)

CONCURRENT_MULTI_CANDIDATE_WARNING = (
    "A concurrent multi-candidate design may increase animal numbers. The investigator "
    "should justify why sequential testing or conditional progression is not scientifically appropriate."
)


@dataclass
class RuleEvaluation:
    readiness: str
    critical_missing: list[str] = field(default_factory=list)
    required_missing: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    considerations: list[str] = field(default_factory=list)

    @property
    def all_missing(self) -> list[str]:
        return _dedupe(self.critical_missing + self.required_missing)

    @property
    def all_warnings_and_considerations(self) -> list[str]:
        return _dedupe(self.warnings + self.considerations)


def _blank(value) -> bool:
    if value is None:
        return True
    if isinstance(value, str):
        return not value.strip()
    if isinstance(value, (list, tuple, set, dict)):
        return len(value) == 0
    return False


def _dedupe(items: list[str]) -> list[str]:
    seen = set()
    result = []
    for item in items:
        if item and item not in seen:
            seen.add(item)
            result.append(item)
    return result


def _selected(response: dict) -> set[str]:
    return set(response.get("test_article_types", []))


def has_adc(response: dict) -> bool:
    return bool(_selected(response) & ADC_TYPES)


def has_free_payload(response: dict) -> bool:
    return bool(_selected(response) & FREE_PAYLOAD_TYPES)


def has_prodrug(response: dict) -> bool:
    return bool(_selected(response) & PRODRUG_TYPES)


def _article_details(response: dict) -> dict:
    return response.get("test_article_details", {}) or {}


def _is_not_decided(value: str) -> bool:
    return _blank(value) or value in {"Not yet decided", "Unknown", "Pending", "Unsure"}


def evaluate_rules(response: dict) -> RuleEvaluation:
    """Evaluate missing information, warnings, considerations, and readiness."""
    critical_missing: list[str] = []
    required_missing: list[str] = []
    warnings: list[str] = []
    considerations: list[str] = []
    aim_or_strategy_unclear = False

    if _blank(response.get("study_title")):
        required_missing.append("Working study title is missing.")

    if response.get("immediate_decision") == "Explore feasibility because the next study is not yet defined":
        warnings.append(DOWNSTREAM_UNDEFINED_WARNING)
        aim_or_strategy_unclear = True

    if response.get("downstream_study") == "No defined downstream study yet":
        warnings.append(DOWNSTREAM_UNDEFINED_WARNING)
        aim_or_strategy_unclear = True

    if _blank(response.get("study_type")) or response.get("study_type") == "Unsure":
        required_missing.append("Proposed study type requires confirmation.")

    if _blank(response.get("downstream_study")):
        required_missing.append("Downstream in vivo study is missing.")

    if not response.get("test_article_types"):
        critical_missing.append("At least one proposed test article type must be selected.")

    if response.get("candidate_count") == "Not yet decided":
        required_missing.append("Candidate comparison strategy requires confirmation.")
        aim_or_strategy_unclear = True

    if response.get("candidate_count") == "More than two candidates":
        warnings.append(MULTIPLE_CANDIDATE_WARNING)
        if response.get("candidate_prioritisation") in {"No", "Unsure", ""}:
            aim_or_strategy_unclear = True

    if has_adc(response):
        _evaluate_adc_details(response, critical_missing, warnings)
        if response.get("adc_target_relevance") == "Unknown":
            considerations.append(ADC_TARGET_RELEVANCE_CONSIDERATION)

    if has_free_payload(response) and has_prodrug(response) and has_adc(response):
        dose_expressions = [
            detail.get("adc_dose_expression", "")
            for detail in _article_details(response).values()
            if detail.get("category") in ADC_TYPES
        ]
        if "payload-equivalent dose" not in dose_expressions:
            warnings.append(PAYLOAD_COMPARISON_WARNING)

    if response.get("sex") in {"Female only", "Male only"} and _blank(response.get("sex_justification")):
        required_missing.append("Single-sex animal use requires a scientific justification.")

    if _blank(response.get("species")) or response.get("species") == "Not yet decided":
        required_missing.append("Animal species requires confirmation.")

    if _blank(response.get("strain")):
        required_missing.append("Animal strain is missing.")

    if _blank(response.get("animal_status")) or response.get("animal_status") == "Not yet decided":
        required_missing.append("Healthy or tumour-bearing model status requires confirmation.")

    downstream_model = response.get("downstream_model", "")
    if _blank(downstream_model) or "to be confirmed" in downstream_model.lower():
        required_missing.append("Downstream animal or tumour model requires confirmation.")

    if _blank(response.get("route")) or response.get("route") == "Not yet decided":
        critical_missing.append("Route of administration requires confirmation.")

    if _blank(response.get("mtd_schedule")) or response.get("mtd_schedule") == "Not yet decided":
        critical_missing.append("MTD dosing schedule requires confirmation.")

    if _repeat_downstream(response) and response.get("mtd_schedule") == "Single dose":
        warnings.append(REPEAT_DOSE_SINGLE_MTD_WARNING)

    if response.get("prior_tolerability") in {"No", "Unknown", ""}:
        considerations.append(NO_PRIOR_TOLERABILITY_CONSIDERATION)

    if response.get("starting_dose_evidence") in {"", "No evidence entered yet"} and _blank(response.get("starting_dose_evidence_text")):
        critical_missing.append("Starting-dose or dose-range rationale requires evidence or investigator explanation.")

    if _blank(response.get("tolerability_definition")):
        critical_missing.append("MTD or tolerability definition is missing.")

    if _blank(response.get("observation_period")):
        critical_missing.append("Observation period after final dose is missing.")

    if _monitoring_frequency_incomplete(response):
        required_missing.append("Monitoring frequency before dosing, after dosing, during the expected toxicity window and after adverse signs requires completion.")

    if _blank(response.get("clinical_scoring")):
        required_missing.append("Clinical scoring system or humane endpoint/intervention criteria are missing.")

    if _blank(response.get("decision_maker")):
        required_missing.append("Authorised decision-maker for intervention, euthanasia or escalation stopping is missing.")

    if _multiple_candidates(response) and response.get("study_design") == "Fixed groups dosed concurrently":
        warnings.append(CONCURRENT_MULTI_CANDIDATE_WARNING)

    critical_missing = _dedupe(critical_missing)
    required_missing = _dedupe(required_missing)
    warnings = _dedupe(warnings)
    considerations = _dedupe(considerations)

    if aim_or_strategy_unclear:
        readiness = "Study aim or comparison strategy requires clarification before design can be finalised"
    elif critical_missing or required_missing:
        readiness = "Design partially complete - critical information required"
    else:
        readiness = "Design information substantially complete for drafting"

    return RuleEvaluation(
        readiness=readiness,
        critical_missing=critical_missing,
        required_missing=required_missing,
        warnings=warnings,
        considerations=considerations,
    )


def _evaluate_adc_details(response: dict, critical_missing: list[str], warnings: list[str]) -> None:
    incomplete = False
    for category, detail in _article_details(response).items():
        if category not in ADC_TYPES and detail.get("category") not in ADC_TYPES:
            continue
        if _blank(detail.get("adc_linker")):
            incomplete = True
            critical_missing.append(f"{category}: linker type or linker description requires confirmation.")
        if detail.get("adc_dar_known") != "Yes":
            incomplete = True
            critical_missing.append(f"{category}: DAR status/value requires confirmation.")
        if detail.get("adc_dose_expression") in {"", "Not yet decided"}:
            incomplete = True
            critical_missing.append(f"{category}: dose-expression basis requires confirmation.")
    if incomplete:
        warnings.append(ADC_INCOMPLETE_WARNING)


def _repeat_downstream(response: dict) -> bool:
    repeat_study = response.get("downstream_study") == "Repeat-dose toxicity study"
    repeat_schedule = response.get("downstream_schedule") in {
        "Once weekly dosing",
        "Multiple doses within one cycle",
        "Multiple treatment cycles",
    }
    return repeat_study or repeat_schedule


def _monitoring_frequency_incomplete(response: dict) -> bool:
    return any(
        _blank(response.get(key))
        for key in [
            "monitoring_routine",
            "monitoring_immediate",
            "monitoring_toxicity_window",
            "monitoring_after_signs",
        ]
    )


def _multiple_candidates(response: dict) -> bool:
    return response.get("candidate_count") in {
        "Two candidates requiring comparison",
        "More than two candidates",
    }
