"""Optional ChatGPT/OpenAI drafting support.

The app can run without an OpenAI API key. When a key is configured, only the
limited study-summary payload is sent for drafting an introduction and aims section.
The study recommendation itself remains rule-based in study_recommender.py.
"""

from __future__ import annotations

import json
import os

from study_recommender import StudyRecommendation


DEFAULT_MODEL = "gpt-5-mini"


def get_openai_settings(secrets=None) -> tuple[str | None, str]:
    api_key = None
    model = DEFAULT_MODEL
    if secrets is not None:
        try:
            api_key = secrets.get("OPENAI_API_KEY")
            model = secrets.get("OPENAI_MODEL", DEFAULT_MODEL)
        except Exception:
            api_key = None
    api_key = api_key or os.getenv("OPENAI_API_KEY")
    model = os.getenv("OPENAI_MODEL", model)
    return api_key, model


def build_rule_based_introduction(response: dict, recommendation: StudyRecommendation) -> str:
    title = response.get("study_title") or "the proposed MTD study"
    article_types = ", ".join(response.get("test_article_types") or []) or "the proposed test article"
    downstream = response.get("downstream_study") or "the planned downstream study"
    route = response.get("route") or "the proposed route"
    schedule = response.get("mtd_schedule") or "the proposed MTD schedule"
    return (
        f"Draft introduction: {title} is intended to support design of an in vivo tolerability study involving {article_types}. "
        f"The supplied study objective is to support {downstream}. Based on the current questionnaire responses, the rule-based recommendation is: "
        f"{recommendation.pathway}. The proposed route is {route} and the proposed MTD schedule is {schedule}. "
        "This wording is a draft aid only and requires investigator, veterinary and Animal Ethics Committee review."
    )


def generate_ai_introduction(response: dict, recommendation: StudyRecommendation, api_key: str, model: str = DEFAULT_MODEL) -> str:
    """Generate a concise project introduction and aims section with OpenAI.

    The prompt forbids invention of dose levels, animal numbers, tumour models,
    references, procedures or welfare thresholds not present in the input.
    """
    from openai import OpenAI

    client = OpenAI(api_key=api_key)
    payload = {
        "study_title": response.get("study_title"),
        "immediate_decision": response.get("immediate_decision"),
        "study_type": response.get("study_type"),
        "downstream_study": response.get("downstream_study"),
        "test_article_types": response.get("test_article_types"),
        "candidate_count": response.get("candidate_count"),
        "species": response.get("species"),
        "strain": response.get("strain"),
        "sex": response.get("sex"),
        "animal_status": response.get("animal_status"),
        "route": response.get("route"),
        "mtd_schedule": response.get("mtd_schedule"),
        "downstream_schedule": response.get("downstream_schedule"),
        "prior_tolerability": response.get("prior_tolerability"),
        "recommended_pathway": recommendation.pathway,
        "recommendation_rationale": recommendation.rationale,
        "assumptions": recommendation.assumptions,
    }
    instructions = (
        "You are drafting concise, conservative wording for an animal ethics study-design aid. "
        "Write a project introduction and aims section in plain scientific English. "
        "Use only the supplied facts. Do not invent dose levels, animal numbers, tumour models, references, procedures, welfare thresholds, approvals or claims of efficacy. "
        "Clearly label uncertain or incomplete items as requiring investigator confirmation. "
        "Do not state that the study is approved or that the tool replaces investigator, veterinary or AEC review."
    )
    prompt = (
        "Draft two sections with headings: 'Project introduction' and 'Project aims'. "
        "Keep the total length under 450 words. Input data:\n"
        + json.dumps(payload, indent=2)
    )
    result = client.responses.create(model=model, instructions=instructions, input=prompt)
    return result.output_text.strip()
