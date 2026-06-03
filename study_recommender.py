"""Rule-based study-design recommendations for the MTD module.

The recommendation engine is intentionally deterministic and transparent. ChatGPT can
be used later to draft prose, but the study pathway and procedure assumptions should
come from explicit rules that can be reviewed by investigators, veterinarians and the
Animal Ethics Committee office.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from config import ADC_TYPES
from rules import evaluate_rules, has_adc


@dataclass
class ProcedureEthicsItem:
    procedure: str
    why_included: str
    associated_pain_or_distress: str
    anaesthesia_or_analgesia_consideration: str
    welfare_cost: str


@dataclass
class StudyRecommendation:
    pathway: str
    summary: str
    rationale: list[str] = field(default_factory=list)
    flowchart_steps: list[str] = field(default_factory=list)
    procedure_items: list[ProcedureEthicsItem] = field(default_factory=list)
    welfare_costs: list[str] = field(default_factory=list)
    three_rs: dict[str, str] = field(default_factory=dict)
    animal_timeline: list[str] = field(default_factory=list)
    assumptions: list[str] = field(default_factory=list)


def recommend_study_design(response: dict) -> StudyRecommendation:
    """Return a conservative design pathway based only on supplied answers."""
    pathway, rationale = _select_pathway(response)
    steps = _build_flowchart_steps(response, pathway)
    procedure_items = _build_procedure_items(response, pathway)
    welfare_costs = _build_welfare_costs(response, procedure_items)
    three_rs = _build_three_rs(response, pathway)
    timeline = _build_animal_timeline(response, pathway)
    assumptions = _build_assumptions(response)

    summary = (
        f"Recommended pathway: {pathway}. This recommendation is based on the stated downstream study, "
        "route/schedule information, prior tolerability evidence, candidate-comparison strategy and ADC readiness fields. "
        "It is a study-design aid only and requires investigator, veterinary and AEC review."
    )
    return StudyRecommendation(
        pathway=pathway,
        summary=summary,
        rationale=rationale,
        flowchart_steps=steps,
        procedure_items=procedure_items,
        welfare_costs=welfare_costs,
        three_rs=three_rs,
        animal_timeline=timeline,
        assumptions=assumptions,
    )


def _select_pathway(response: dict) -> tuple[str, list[str]]:
    rationale: list[str] = []
    downstream = response.get("downstream_study", "")
    downstream_schedule = response.get("downstream_schedule", "")
    mtd_schedule = response.get("mtd_schedule", "")
    candidate_count = response.get("candidate_count", "")
    prior_tolerability = response.get("prior_tolerability", "")
    evaluation = evaluate_rules(response)

    if downstream == "No defined downstream study yet" or response.get("immediate_decision") == "Explore feasibility because the next study is not yet defined":
        rationale.append("The downstream use of the tolerability information has not yet been defined.")
        rationale.append("A limited preliminary tolerability/feasibility design is more conservative than a full MTD determination until the progression decision is clarified.")
        return "Preliminary tolerability or feasibility study before full MTD design", rationale

    if candidate_count in {"Two candidates requiring comparison", "More than two candidates"}:
        rationale.append("More than one candidate is being considered for animal tolerability testing.")
        rationale.append("A staged or conditional design can reduce animal use by avoiding full parallel testing unless justified by the scientific objective.")
        return "Staged candidate-comparison tolerability design", rationale

    if has_adc(response) and _adc_design_incomplete(response):
        rationale.append("At least one ADC is proposed and ADC design information is incomplete.")
        rationale.append("Linker description, DAR and dose-expression basis should be confirmed before final ADC MTD comparisons or payload-equivalent interpretation.")
        return "ADC-readiness confirmation followed by staged tolerability design", rationale

    if _is_repeat_downstream(downstream, downstream_schedule):
        rationale.append("The downstream study or schedule involves repeat dosing.")
        if mtd_schedule == "Single dose":
            rationale.append("The proposed MTD schedule is single dose, which may not adequately support a repeat-dose downstream regimen.")
        if prior_tolerability in {"No", "Unknown", ""}:
            rationale.append("Prior tolerability evidence in the same species/route/schedule has not been supplied.")
            return "Staged repeat-dose tolerability study with escalation-stop review", rationale
        return "Repeat-dose tolerability study aligned to downstream schedule", rationale

    if downstream_schedule == "Single dose" or mtd_schedule == "Single dose":
        rationale.append("The stated downstream or MTD schedule is single dose.")
        if prior_tolerability in {"No", "Unknown", ""}:
            rationale.append("No prior tolerability evidence has been supplied, so staged escalation and stop criteria are important.")
        return "Single-dose staged dose-escalation tolerability study", rationale

    if evaluation.critical_missing:
        rationale.append("Critical design information is still missing, so a final pathway cannot be treated as complete.")
        return "Clarify missing critical information before finalising MTD design", rationale

    rationale.append("The supplied information supports a staged tolerability design aligned to the intended downstream route and schedule.")
    return "Staged MTD/tolerability study aligned to downstream design", rationale


def _adc_design_incomplete(response: dict) -> bool:
    for category, detail in (response.get("test_article_details") or {}).items():
        if category in ADC_TYPES or detail.get("category") in ADC_TYPES:
            if not detail.get("adc_linker"):
                return True
            if detail.get("adc_dar_known") != "Yes":
                return True
            if detail.get("adc_dose_expression") in {"", "Not yet decided"}:
                return True
    return False


def _is_repeat_downstream(downstream: str, schedule: str) -> bool:
    return downstream == "Repeat-dose toxicity study" or schedule in {
        "Once weekly dosing",
        "Multiple doses within one cycle",
        "Multiple treatment cycles",
    }


def _build_flowchart_steps(response: dict, pathway: str) -> list[str]:
    steps = [
        "Animals arrive, are identified and acclimatise under facility SOPs",
        "Baseline health checks, bodyweight and clinical observations are recorded",
    ]

    if response.get("animal_status") in {"Tumour-bearing", "Both, using a staged design"}:
        steps.append("Confirm tumour-model status and tumour-burden monitoring requirements, if applicable")

    if "ADC-readiness" in pathway:
        steps.append("Confirm ADC linker, DAR, free payload/purity status and dose-expression basis")

    if "candidate-comparison" in pathway:
        steps.append("Prioritise candidates using non-animal and formulation/conjugation information before animal dosing")
        steps.append("Dose the lead candidate first; progress additional candidates only if justified by results")
    else:
        steps.append("Allocate animals to starting cohort or planned treatment group")

    if "repeat-dose" in pathway.lower():
        steps.append("Administer repeat dosing on the proposed schedule with predefined monitoring windows")
    elif "single-dose" in pathway.lower():
        steps.append("Administer a single dose to the starting cohort with close post-dose monitoring")
    elif "feasibility" in pathway.lower():
        steps.append("Run a limited preliminary tolerability cohort to clarify feasibility before full MTD design")
    else:
        steps.append("Administer dose cohort according to the proposed route and MTD schedule")

    steps.extend(
        [
            "Monitor bodyweight, clinical signs and any route-specific effects against humane endpoint criteria",
            "Review cohort tolerability before escalation, repeat dosing or progression to additional arms",
            "Stop escalation or intervene if humane endpoints or unacceptable cohort toxicity occur",
            "Select tolerated dose/range for downstream planning, or document why design cannot be finalised",
            "Complete end-of-study disposition, humane killing and/or tissue collection only as approved in the protocol",
        ]
    )
    return steps


def _build_procedure_items(response: dict, pathway: str) -> list[ProcedureEthicsItem]:
    route = response.get("route") or "route not yet decided"
    monitoring = response.get("monitoring_parameters") or ["clinical observations", "bodyweight"]
    items = [
        ProcedureEthicsItem(
            procedure="Arrival, acclimatisation, identification and baseline checks",
            why_included="Needed to confirm animals are suitable for dosing and to establish baseline welfare observations.",
            associated_pain_or_distress="Expected to involve handling and possible transient stress associated with transport, acclimatisation and identification procedures.",
            anaesthesia_or_analgesia_consideration="Not usually inferred from the questionnaire; confirm if the facility identification method requires additional measures.",
            welfare_cost="Low procedural burden if animals acclimatise normally, but transport and handling are still welfare costs to acknowledge.",
        ),
        ProcedureEthicsItem(
            procedure=f"Test article administration by {route}",
            why_included="Central experimental procedure required to assess tolerability of the proposed therapy class.",
            associated_pain_or_distress=_route_distress_text(route),
            anaesthesia_or_analgesia_consideration=_route_anaesthesia_text(route),
            welfare_cost="Potential acute handling/procedural stress plus possible treatment-related clinical toxicity depending on dose and compound behaviour.",
        ),
        ProcedureEthicsItem(
            procedure="Clinical monitoring and bodyweight assessment",
            why_included=f"Needed to detect adverse effects and apply humane endpoints. Proposed monitoring includes: {', '.join(monitoring)}.",
            associated_pain_or_distress="Repeated handling may cause mild transient stress; monitoring itself is intended to reduce unrecognised welfare impact.",
            anaesthesia_or_analgesia_consideration="Not required for routine visual monitoring or weighing, unless local SOPs specify otherwise for a related procedure.",
            welfare_cost="Cumulative handling burden balanced against earlier detection of toxicity and timely intervention.",
        ),
    ]

    if "repeat-dose" in pathway.lower() or response.get("mtd_schedule") in {"Once weekly dosing", "Multiple doses within one cycle", "Multiple treatment cycles"}:
        items.append(
            ProcedureEthicsItem(
                procedure="Repeat dosing and repeated post-dose observations",
                why_included="The downstream plan involves repeat dosing or the MTD schedule is intended to model repeated exposure.",
                associated_pain_or_distress="Repeated restraint, dosing and monitoring can increase cumulative stress and may compound treatment-related toxicity.",
                anaesthesia_or_analgesia_consideration="Confirm whether any repeat procedure requires anaesthesia, analgesia or altered monitoring under institutional SOPs.",
                welfare_cost="Higher cumulative welfare burden than a single-dose design because animals experience repeated procedures and repeated toxicity-risk windows.",
            )
        )

    if response.get("animal_status") in {"Tumour-bearing", "Both, using a staged design"}:
        items.append(
            ProcedureEthicsItem(
                procedure="Tumour-related monitoring or model procedures, if included in this protocol",
                why_included="Tumour-bearing animals were selected or may be used in a staged design.",
                associated_pain_or_distress="Tumour burden can contribute to welfare impact through growth-related effects, handling for measurement and possible procedure-related distress if tumour establishment is part of the protocol.",
                anaesthesia_or_analgesia_consideration="Confirm whether tumour implantation, imaging or sampling is part of this protocol and whether anaesthesia/analgesia is required.",
                welfare_cost="Potential additional welfare cost beyond drug tolerability, especially if tumour establishment and treatment toxicity overlap.",
            )
        )

    items.extend(
        [
            ProcedureEthicsItem(
                procedure="Blood collection, if requested by the final protocol",
                why_included="Not automatically required for an MTD-only design, but may be requested for safety, exposure or PK-related interpretation.",
                associated_pain_or_distress="Pain/distress depends on sampling site, volume, frequency, restraint and technique. These details must be specified before ethics submission.",
                anaesthesia_or_analgesia_consideration="Anaesthesia or local measures depend on the sampling method and institutional SOP; the current questionnaire has not supplied enough detail to determine this.",
                welfare_cost="Can add procedural and cumulative blood-volume burden; should be justified if not essential to the MTD decision.",
            ),
            ProcedureEthicsItem(
                procedure="Humane killing/euthanasia at humane endpoint or study end, if required",
                why_included="Animals reaching humane endpoints require timely intervention, and end-of-study disposition must be defined in the approved protocol.",
                associated_pain_or_distress="The welfare impact depends on the approved method and how it is performed. If CO2 is proposed, method, displacement/fill rate and confirmation/secondary method should follow institutional SOPs.",
                anaesthesia_or_analgesia_consideration="Confirm the approved euthanasia method with the veterinarian/AEC. Do not assume CO2 is acceptable without local approval.",
                welfare_cost="Terminal procedure and any preceding clinical deterioration are major welfare considerations that must be minimised through humane endpoints and trained staff.",
            ),
            ProcedureEthicsItem(
                procedure="Necropsy or tissue collection, if requested by the final protocol",
                why_included="May be useful to interpret toxicity, but is not automatically required by this recommendation.",
                associated_pain_or_distress="If performed after humane killing, direct pain to the animal is avoided; any ante-mortem sampling or imaging must be separately justified.",
                anaesthesia_or_analgesia_consideration="Confirm whether tissue collection is terminal only or involves any live-animal procedure.",
                welfare_cost="Minimal additional live-animal welfare cost if terminal-only, but still affects animal disposition and protocol detail.",
            ),
        ]
    )
    return items


def _route_distress_text(route: str) -> str:
    route_lower = route.lower()
    if "intravenous" in route_lower:
        return "Likely restraint, venepuncture/cannulation-related discomfort and possible injection-site or extravasation risk; severity depends on technique and frequency."
    if "intraperitoneal" in route_lower:
        return "Likely handling/restraint and needle insertion discomfort, with potential abdominal injection-related complications if technique or formulation is unsuitable."
    if "subcutaneous" in route_lower:
        return "Likely handling/restraint and needle insertion discomfort, with possible local injection-site effects depending on formulation and volume."
    if "oral" in route_lower:
        return "Likely restraint and gavage-related stress if oral gavage is used; formulation tolerance and aspiration risk require consideration."
    if "not yet" in route_lower:
        return "Route is not yet decided, so procedure-related pain or distress cannot be finalised."
    return "Procedure-related pain or distress depends on the selected route, restraint method, formulation and dosing frequency."


def _route_anaesthesia_text(route: str) -> str:
    route_lower = route.lower()
    if "intravenous" in route_lower:
        return "Anaesthesia is not assumed; confirm whether restraint, warming, cannulation or local SOPs require additional measures."
    if "intraperitoneal" in route_lower or "subcutaneous" in route_lower:
        return "Anaesthesia is not assumed for routine dosing; confirm against institutional SOP and formulation-specific concerns."
    if "oral" in route_lower:
        return "Anaesthesia is not assumed for routine oral dosing/gavage; confirm operator training and local SOP requirements."
    return "Anaesthesia requirement cannot be determined from the supplied route information and should be confirmed before submission."


def _build_welfare_costs(response: dict, procedure_items: list[ProcedureEthicsItem]) -> list[str]:
    costs = [item.welfare_cost for item in procedure_items]
    if response.get("prior_tolerability") in {"No", "Unknown", ""}:
        costs.append("Unknown tolerability increases uncertainty around treatment-related welfare costs; staged escalation and stop criteria are important refinements.")
    if has_adc(response):
        costs.append("For ADCs, interpretation of welfare impact may depend on target relevance, linker stability, free payload content and dose-expression basis.")
    return costs


def _build_three_rs(response: dict, pathway: str) -> dict[str, str]:
    non_animal = response.get("non_animal_information") or []
    replacement = (
        "Use existing non-animal and prior evidence before animal dosing. "
        f"Information listed by the investigator: {', '.join(non_animal) if non_animal else 'not yet supplied'}. "
        "The in vivo component should be justified as necessary to assess whole-animal tolerability before downstream in vivo work."
    )
    reduction = (
        response.get("reduction_measures")
        or response.get("animal_number_minimisation")
        or "Use staged escalation, small cohorts and conditional progression to avoid unnecessary candidate arms or dose groups."
    )
    if "candidate-comparison" in pathway:
        reduction += " Candidate arms should be prioritised before animal testing where possible, with later arms conditional on lead-candidate results."
    refinement = (
        response.get("refinement_measures")
        or "Use baseline assessment, defined monitoring frequency, humane endpoints, escalation-stop rules, supportive care where compatible with interpretation, and prompt veterinary/investigator review."
    )
    return {"Replacement": replacement, "Reduction": reduction, "Refinement": refinement}


def _build_animal_timeline(response: dict, pathway: str) -> list[str]:
    timeline = [
        "Animals arrive at the facility and undergo receipt checks, identification and acclimatisation according to local SOPs.",
        "Before dosing, staff record baseline health, bodyweight and relevant clinical observations; animals are excluded or reviewed if baseline welfare concerns are identified.",
    ]
    if response.get("animal_status") in {"Tumour-bearing", "Both, using a staged design"}:
        timeline.append("If tumour-bearing animals are used, tumour-model establishment or confirmation and tumour-burden monitoring must be described before dosing begins.")
    timeline.append("Animals are allocated to the starting dose cohort or treatment group according to the approved design.")
    if "ADC-readiness" in pathway:
        timeline.append("Before final dosing design is locked, ADC linker, DAR, free payload/purity status and dose-expression basis are confirmed.")
    if "candidate-comparison" in pathway:
        timeline.append("The lead candidate is tested first where scientifically appropriate; additional candidate arms proceed only after review of lead-candidate tolerability and reduction justification.")
    timeline.extend(
        [
            "The test article is administered by the proposed route and animals enter the defined post-dose monitoring period.",
            "Clinical signs, bodyweight and route-specific effects are reviewed against humane endpoint and cohort-stopping criteria.",
            "Escalation, repeat dosing or progression to further groups occurs only after investigator and welfare review confirms this is justified.",
            "At study completion or humane endpoint, animals undergo the approved disposition, humane killing/euthanasia method and any terminal tissue collection specified in the protocol.",
        ]
    )
    return timeline


def _build_assumptions(response: dict) -> list[str]:
    assumptions = [
        "The recommendation does not set numerical dose levels or animal numbers.",
        "Procedures must be checked against institutional SOPs, veterinary advice and AEC requirements.",
        "Blood collection, anaesthesia, imaging, tumour implantation and necropsy are not assumed unless explicitly included in the final protocol.",
    ]
    if not response.get("route") or response.get("route") == "Not yet decided":
        assumptions.append("Route of administration is not confirmed, so route-specific procedure burden is provisional.")
    if not response.get("clinical_scoring"):
        assumptions.append("Humane endpoint/intervention criteria are not fully supplied.")
    return assumptions
