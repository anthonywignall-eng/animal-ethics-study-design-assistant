from data_model import completed_example_response, worked_adc_incomplete_response
from report_generator import build_report_sections, generate_docx_report


def test_report_generation_returns_docx_bytes():
    response = completed_example_response()

    output = generate_docx_report(response)

    assert output.getbuffer().nbytes > 1000
    assert output.getvalue()[:2] == b"PK"


def test_worked_example_report_contains_expected_flags():
    response = worked_adc_incomplete_response()

    sections = build_report_sections(response)

    assert sections["readiness"] == "Design partially complete - critical information required"
    assert any("DAR" in item for item in sections["critical_missing"])
    assert any("linker" in item.lower() for item in sections["critical_missing"])
    assert any("dose-expression" in item.lower() for item in sections["critical_missing"])
    assert any("ADC design information is incomplete" in item for item in sections["warnings"])
    assert any("staged escalation" in item for item in sections["warnings"])
