"""Streamlit entry point for the Animal Ethics Study Design Assistant."""

import streamlit as st

from config import APP_TITLE, ETHICS_DISCLAIMER, LANDING_TEXT, STAGES
from data_model import empty_response
from questionnaire import render_stage
from report_generator import build_report_sections, generate_docx_report


REPORT_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"


st.set_page_config(page_title=APP_TITLE, page_icon="AE", layout="wide")


def initialise_session() -> None:
    if "response" not in st.session_state:
        st.session_state.response = empty_response()
    if "step" not in st.session_state:
        st.session_state.step = 0


def reset_questionnaire() -> None:
    st.session_state.response = empty_response()
    st.session_state.step = 0


def apply_styles() -> None:
    st.markdown(
        """
        <style>
        .main .block-container {max-width: 1180px; padding-top: 2rem; padding-bottom: 3rem;}
        h1, h2, h3 {color: #1f2933;}
        .small-note {color: #52606d; font-size: 0.92rem; line-height: 1.45;}
        .disclaimer {border-left: 4px solid #52606d; background: #f5f7fa; padding: 0.9rem 1rem; color: #243b53;}
        .readiness {border: 1px solid #bcccdc; border-radius: 6px; padding: 1rem; background: #f8fafc;}
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
        st.caption("Responses are held in this browser session only. Version 1 does not use a database, login system, external AI, or external API calls.")


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


def render_results() -> None:
    response = st.session_state.response
    sections = build_report_sections(response)

    st.subheader("Stage 10: Final Review and Report")
    st.markdown(f"<div class='readiness'><strong>Readiness classification:</strong><br>{sections['readiness']}</div>", unsafe_allow_html=True)

    st.header("Proposed Study Summary")
    st.write(sections["study_summary"])

    st.header("Proposed Test Articles")
    st.table(sections["test_articles"] or [{"Status": "No test articles supplied"}])

    st.header("Proposed Animal Model")
    st.table(sections["animal_model"])

    st.header("Route and Schedule")
    st.table(sections["route_schedule"])

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
        st.header("Animal-use Reduction Strategy")
        for key, value in sections["reduction"].items():
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
