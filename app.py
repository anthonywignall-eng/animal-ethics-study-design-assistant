"""Streamlit entry point for the Animal Ethics Study Design Assistant."""

import streamlit as st

from ai_writer import generate_ai_introduction, get_openai_settings
from config import APP_TITLE, ETHICS_DISCLAIMER, LANDING_TEXT, STAGES
from data_model import empty_response
from flowchart import render_flowchart_png
from questionnaire import render_stage
from report_generator import build_report_sections, generate_docx_report


REPORT_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"


st.set_page_config(page_title=APP_TITLE, page_icon=":clipboard:", layout="wide")


def initialise_session() -> None:
    if "response" not in st.session_state:
        st.session_state.response = empty_response()
    if "step" not in st.session_state:
        st.session_state.step = 0


def reset_questionnaire() -> None:
    st.session_state.response = empty_response()
    st.session_state.step = 0
    st.session_state.pop("project_intro_text", None)


def apply_styles() -> None:
    st.markdown(
        """
        <style>
        .main .block-container {max-width: 1180px; padding-top: 2rem; padding-bottom: 3rem;}
        h1, h2, h3 {color: #1f2933;}
        .small-note {color: #52606d; font-size: 0.92rem; line-height: 1.45;}
        .disclaimer {border-left: 4px solid #52606d; background: #f5f7fa; padding: 0.9rem 1rem; color: #243b53;}
        .readiness {border: 1px solid #bcccdc; border-radius: 6px; padding: 1rem; background: #f8fafc;}
        .recommendation {border: 1px solid #9fb3c8; border-radius: 6px; padding: 1rem; background: #f7fbff;}
        div.stButton > button {border-radius: 6px;}
        </style>
        """,
        unsafe_allow_html=True,
    )


def render_landing() -> None:
    st.title(APP_TITLE)
    for paragraph in LANDING_TEXT:
        st.write(paragraph)
    st.markdown(f"<div class='disclaimer'><strong>Important:</strong> {ETHICS_DISCLAIMER}</div>", unsafe_allow_html=True)
    st.write("")
    if st.button("Start questionnaire", type="primary"):
        st.session_state.step = 1
        st.rerun()


def render_sidebar() -> None:
    with st.sidebar:
        st.header("Progress")
        if st.session_state.step == 0:
            st.progress(0)
            st.write("Landing page")
        elif st.session_state.step <= len(STAGES):
            stage_index = st.session_state.step - 1
            st.progress(st.session_state.step / (len(STAGES) + 1))
            st.write(f"Stage {st.session_state.step} of {len(STAGES)}")
            st.write(STAGES[stage_index]["title"])
        else:
            st.progress(1.0)
            st.write("Final review and report")
        st.divider()
        st.caption("Responses are held in this browser session only. If optional OpenAI drafting is enabled, the selected summary fields are sent to OpenAI to draft report wording.")


def render_navigation() -> None:
    st.divider()
    previous_col, spacer_col, next_col = st.columns([1, 4, 1])
    with previous_col:
        if st.button("Previous", disabled=st.session_state.step <= 1):
            st.session_state.step -= 1
            st.rerun()
    with next_col:
        label = "Review results" if st.session_state.step == len(STAGES) else "Next"
        if st.button(label, type="primary"):
            st.session_state.step += 1
            st.rerun()


def render_project_intro_editor(response: dict, sections: dict) -> None:
    recommendation = sections["recommendation"]
    api_key, model = get_openai_settings(st.secrets)

    st.header("Project Introduction and Aims")
    if api_key:
        st.caption("Optional ChatGPT drafting is enabled. It drafts wording only; the study recommendation remains rule-based.")
        if st.button("Generate AI introduction and aims"):
            with st.spinner("Drafting introduction and aims..."):
                try:
                    response["ai_introduction"] = generate_ai_introduction(response, recommendation, api_key=api_key, model=model)
                    st.session_state.project_intro_text = response["ai_introduction"]
                    st.success("Draft introduction generated. Please review and edit before using it.")
                except Exception as error:
                    st.error(f"Could not generate the AI draft: {error}")
    else:
        st.info("OpenAI drafting is not enabled, so the app is using a rule-based introduction. Add OPENAI_API_KEY in Streamlit secrets to enable ChatGPT drafting.")

    intro_default = response.get("ai_introduction") or sections["project_introduction"]
    if "project_intro_text" not in st.session_state:
        st.session_state.project_intro_text = intro_default
    response["ai_introduction"] = st.text_area(
        "Report introduction and aims",
        value=st.session_state.project_intro_text,
        height=220,
        key="project_intro_text",
        help="This text will be included in the Word report. Edit it freely before downloading.",
    )


def render_results() -> None:
    response = st.session_state.response
    sections = build_report_sections(response)
    recommendation = sections["recommendation"]

    st.subheader("Stage 10: Final Review and Report")
    st.markdown(f"<div class='readiness'><strong>Readiness classification:</strong><br>{sections['readiness']}</div>", unsafe_allow_html=True)

    st.header("Recommended Study Design")
    st.markdown(f"<div class='recommendation'><strong>{recommendation.pathway}</strong><br>{recommendation.summary}</div>", unsafe_allow_html=True)
    if recommendation.rationale:
        st.markdown("**Why this pathway was selected**")
        for item in recommendation.rationale:
            st.write(f"- {item}")

    st.markdown("**Rule-based study flowchart**")
    st.image(render_flowchart_png(recommendation.flowchart_steps).getvalue(), caption="Recommended MTD study pathway based on supplied answers")

    render_project_intro_editor(response, sections)

    st.header("Proposed Study Summary")
    st.write(sections["study_summary"])

    st.header("Proposed Test Articles")
    st.table(sections["test_articles"] or [{"Status": "No test articles supplied"}])

    st.header("Proposed Animal Model")
    st.table(sections["animal_model"])

    st.header("Route and Schedule")
    st.table(sections["route_schedule"])

    st.header("Animal Ethics Procedure Considerations")
    st.caption("These are rule-based procedure considerations and must be checked against the final protocol, facility SOPs, veterinary advice and AEC requirements.")
    st.dataframe(sections["procedure_ethics"], use_container_width=True, hide_index=True)

    st.header("Welfare Cost to Animals")
    for item in sections["welfare_costs"]:
        st.write(f"- {item}")

    st.header("Animal Timeline")
    for index, item in enumerate(sections["animal_timeline"], start=1):
        st.write(f"{index}. {item}")

    left, right = st.columns(2)
    with left:
        st.header("Monitoring and Welfare Framework")
        for key, value in sections["welfare"].items():
            st.markdown(f"**{key}**")
            if isinstance(value, dict):
                for nested_key, nested_value in value.items():
                    st.write(f"- {nested_key}: {nested_value}")
            else:
                st.write(value)
    with right:
        st.header("Replacement, Reduction and Refinement")
        for key, value in sections["three_rs"].items():
            st.markdown(f"**{key}**")
            st.write(value)

    st.header("Critical Missing Information")
    if sections["critical_missing"]:
        for item in sections["critical_missing"]:
            st.write(f"- {item}")
    else:
        st.success("No critical missing information identified by the current rule set.")

    st.header("Design Warnings and Considerations")
    if sections["warnings"]:
        for item in sections["warnings"]:
            st.write(f"- {item}")
    else:
        st.success("No design warnings or considerations identified by the current rule set.")

    st.header("Assumptions and Items Requiring Protocol Confirmation")
    for item in sections["assumptions"]:
        st.write(f"- {item}")

    st.header("Draft Ethics-application Content")
    st.caption("Draft wording is generated from supplied answers and rule-based prompts only. It requires investigator review.")
    for heading, text in sections["draft_ethics"].items():
        with st.expander(heading, expanded=False):
            st.write(text)

    report = generate_docx_report(response)
    file_title = response.get("study_title") or "mtd_study_design_report"
    safe_title = "".join(character if character.isalnum() else "_" for character in file_title).strip("_").lower()
    st.download_button(
        "Download Word report",
        data=report,
        file_name=f"{safe_title or 'mtd_study_design_report'}.docx",
        mime=REPORT_MIME,
        type="primary",
    )

    if st.button("Start a new questionnaire"):
        reset_questionnaire()
        st.rerun()

    st.divider()
    if st.button("Back to previous stage"):
        st.session_state.step = len(STAGES)
        st.rerun()


def main() -> None:
    initialise_session()
    apply_styles()
    render_sidebar()

    if st.session_state.step == 0:
        render_landing()
        return

    if st.session_state.step <= len(STAGES):
        render_stage(st.session_state.step - 1, st.session_state.response)
        render_navigation()
        return

    render_results()


if __name__ == "__main__":
    main()
