"""Rule-based drafting support for report introduction and aims.

This module deliberately avoids external AI/API calls. It creates conservative,
editable draft wording from the supplied questionnaire responses and the transparent
study recommendation. Compatibility functions are retained so older app code cannot
crash, but they never enable or call OpenAI.
"""

from __future__ import annotations

from study_recommender import StudyRecommendation


def get_openai_settings(secrets=None) -> tuple[None, str]:
    """Compatibility shim: OpenAI drafting is disabled in this version."""
    return None, ""


def build_rule_based_introduction(response: dict, recommendation: StudyRecommendation) -> str:
    title = response.get("study_title") or "the proposed MTD/tolerability study"
    article_types = ", ".join(response.get("test_article_types") or []) or "the proposed test article"
    downstream = response.get("downstream_study") or "the planned downstream study"
    route = response.get("route") or "the proposed route"
    schedule = response.get("mtd_schedule") or "the proposed MTD/tolerability schedule"
    decision = response.get("immediate_decision") or "the stated downstream development decision"
    return (
        f"Draft introduction and aims: {title} is intended to support design of an in vivo tolerability study involving {article_types}. "
        f"The supplied study objective is to support {decision}, with the downstream context recorded as {downstream}. "
        f"Based on the current questionnaire responses, the rule-based recommendation is: {recommendation.pathway}. "
        f"The proposed route is {route} and the proposed MTD/tolerability schedule is {schedule}. "
        "The aim is to identify a scientifically justified and welfare-aware tolerability pathway for later protocol drafting, "
        "while documenting missing information, procedure-related welfare considerations and items requiring investigator, "
        "veterinary and Animal Ethics Committee review. This wording is a draft aid only and must be reviewed before use in an ethics application."
    )


def generate_ai_introduction(response: dict, recommendation: StudyRecommendation, api_key: str | None = None, model: str = "") -> str:
    """Compatibility shim: return the rule-based draft instead of calling an API."""
    return build_rule_based_introduction(response, recommendation)
