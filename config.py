"""Configuration for the MTD study-design assistant.

Future study modules can be added by creating a module-specific stage list,
option set, and rule registry, then selecting that module from the Streamlit
entry point. Version 1 ships only the MTD module requested for the prototype.
"""

APP_TITLE = "Animal Ethics Study Design Assistant - MTD Module"

ETHICS_DISCLAIMER = (
    "This report is a study-design aid and draft-preparation document only. "
    "It does not constitute ethics approval or replace review by the investigator, "
    "veterinarian or Animal Ethics Committee."
)

LANDING_TEXT = [
    "This guided tool assists researchers in defining the scientific and welfare-related "
    "components of an in vivo maximum tolerated dose study involving exatecan, SN-38, "
    "related prodrugs or antibody-drug conjugates.",
    "Your responses will be used to recommend a study-design pathway, generate a flowchart, "
    "identify missing information, highlight animal ethics considerations and provide draft "
    "wording prompts for preparation of an animal ethics application.",
    "This tool does not replace investigator judgement, veterinary advice or review and "
    "approval by an Animal Ethics Committee.",
]

STAGES = [
    {"id": "core_project", "title": "Core Aim and Test Article"},
    {"id": "article_readiness", "title": "Compound or ADC Readiness"},
    {"id": "design_drivers", "title": "Model, Route and Schedule"},
    {"id": "welfare_use", "title": "Welfare and Animal-use Essentials"},
]

MODULE_REGISTRY = {
    "mtd": {
        "name": "Maximum tolerated dose study",
        "stages": STAGES,
        "rule_module": "rules.evaluate_rules",
    },
    # Future modules can register their own stages and rule modules here.
    # Examples: "pk", "biodistribution", "tumour_efficacy", "rna_lnp".
}

IMMEDIATE_DECISIONS = [
    "Identify a tolerated dose for a later efficacy study",
    "Identify a tolerated dose for a later PK or biodistribution study",
    "Identify a tolerated dose for a later repeat-dose toxicity study",
    "Compare tolerability of multiple candidate therapies",
    "Explore feasibility because the next study is not yet defined",
    "Other",
]

STUDY_TYPES = [
    "Maximum tolerated dose study",
    "Preliminary dose-range tolerability study",
    "Comparative tolerability study",
    "Unsure",
]

DOWNSTREAM_STUDIES = [
    "Tumour efficacy study",
    "PK study",
    "Biodistribution study",
    "Repeat-dose toxicity study",
    "Combination study",
    "No defined downstream study yet",
    "Other",
]

TEST_ARTICLE_TYPES = [
    "Free exatecan",
    "Free SN-38",
    "Exatecan-derived prodrug",
    "SN-38-derived prodrug",
    "Exatecan-based ADC",
    "SN-38-based ADC",
    "Other",
]

CANDIDATE_COUNTS = [
    "One lead candidate only",
    "Two candidates requiring comparison",
    "More than two candidates",
    "Not yet decided",
]

YES_NO_UNSURE = ["Yes", "No", "Unsure"]
YES_NO_PENDING = ["Yes", "No", "Pending"]
YES_NO_UNKNOWN = ["Yes", "No", "Unknown"]

FREE_PAYLOAD_TYPES = {"Free exatecan", "Free SN-38"}
PRODRUG_TYPES = {"Exatecan-derived prodrug", "SN-38-derived prodrug"}
ADC_TYPES = {"Exatecan-based ADC", "SN-38-based ADC"}

SPECIES_OPTIONS = ["Mouse", "Rat", "Other", "Not yet decided"]
SEX_OPTIONS = ["Female only", "Male only", "Both sexes", "Not yet decided"]
MODEL_OPTIONS = [
    "Healthy/non-tumour-bearing",
    "Tumour-bearing",
    "Both, using a staged design",
    "Not yet decided",
]

ROUTE_OPTIONS = [
    "Intravenous",
    "Intraperitoneal",
    "Subcutaneous",
    "Oral",
    "Other",
    "Not yet decided",
]

SCHEDULE_OPTIONS = [
    "Single dose",
    "Once weekly dosing",
    "Multiple doses within one cycle",
    "Multiple treatment cycles",
    "Not yet decided",
    "Other",
]

PRIOR_TOLERABILITY_OPTIONS = ["Yes", "Partially", "No", "Unknown"]

DOSE_EVIDENCE_OPTIONS = [
    "Published literature",
    "Previous internal study",
    "Closely related compound",
    "Payload-equivalent estimate",
    "No evidence entered yet",
    "Other",
]

OUTCOME_OPTIONS = [
    "Absolute MTD",
    "Recommended tolerated dose for later efficacy studies",
    "Tolerated dose range",
    "Preliminary tolerability information only",
    "Unsure",
]

NOT_TOLERATED_FINDINGS = [
    "Humane endpoint reached",
    "Excessive bodyweight loss",
    "Severe or persistent reduction in activity",
    "Poor grooming/posture",
    "Inability to eat or drink",
    "Dehydration",
    "Severe diarrhoea",
    "Injection-site effects",
    "Other",
]

MONITORING_PARAMETERS = [
    "Bodyweight",
    "General appearance/grooming",
    "Posture",
    "Activity/behaviour",
    "Food intake",
    "Water intake/hydration",
    "Faecal output/diarrhoea",
    "Injection site",
    "Tumour burden, if applicable",
    "Other",
]

SUPPORTIVE_CARE_OPTIONS = [
    "Hydrogel or hydration support",
    "Softened food or food placed on cage floor",
    "Increased monitoring",
    "Veterinary review",
    "Warming/supportive housing measures",
    "Other",
    "None proposed yet",
]

STUDY_DESIGN_OPTIONS = [
    "Sequential dose escalation using small cohorts",
    "Fixed groups dosed concurrently",
    "Staged design with later candidate testing only if required",
    "Not yet decided",
]

ADDITIONAL_CONTROLS = [
    "Free payload comparator",
    "Unconjugated antibody control",
    "Non-binding ADC control",
    "Prodrug comparator",
    "None currently proposed",
    "Other",
]

NON_ANIMAL_INFO = [
    "In vitro potency data",
    "Cell viability studies",
    "Formulation characterisation",
    "ADC conjugation/DAR data",
    "Stability data",
    "Published in vivo evidence",
    "Previous internal data",
    "None entered",
    "Other",
]

DEFAULT_TOLERABILITY_DEFINITION = (
    "The highest administered dose that does not result in mortality, humane endpoint "
    "intervention or unacceptable treatment-related clinical toxicity during the defined "
    "observation period."
)

DEFAULT_INDIVIDUAL_ENDPOINT_ACTION = (
    "The animal will be euthanised humanely and the dose cohort reviewed before any further escalation."
)

DEFAULT_COHORT_TOXICITY_ACTION = (
    "The dose will be considered not tolerated and escalation will not proceed without "
    "investigator and welfare review."
)

DEFAULT_REDUCTION_DRAFT = (
    "Animal use may be reduced through staged dose escalation, prioritisation of lead "
    "candidates before animal testing and progression to additional candidate arms only "
    "where required by the results of the lead-candidate study."
)
