# Animal Ethics Study Design Assistant - MTD Module

A Streamlit prototype for collecting structured study-design information before drafting an animal ethics application for an in vivo maximum tolerated dose (MTD) study involving exatecan, SN-38, related prodrugs, or antibody-drug conjugates (ADCs).

This tool assists with study design and draft preparation only. It does not constitute ethics approval, guarantee Animal Ethics Committee approval, replace investigator judgement, replace veterinary advice, or replace Animal Ethics Committee review.

## What the app does

- Guides a researcher or project lead through a staged MTD study-design questionnaire.
- Shows relevant follow-up questions for free payloads, prodrugs, and ADCs.
- Flags missing critical information and design considerations.
- Generates a structured results page.
- Creates a downloadable Word report for internal draft preparation.
- Keeps responses in the current Streamlit session only.

## What the app does not do

- It does not approve an ethics application.
- It does not provide veterinary, regulatory, or ethics approval advice.
- It does not recommend numerical dose levels.
- It does not call external AI systems or APIs.
- It does not save study information to a database.
- It does not include login, user accounts, or persistent records in Version 1.

## Files in this prototype

- `app.py` - main Streamlit application.
- `questionnaire.py` - questionnaire rendering and conditional question logic.
- `config.py` - app text, stage definitions, option lists, and default wording.
- `rules.py` - rule engine for missing information, warnings, considerations, and readiness classification.
- `report_generator.py` - structured report assembly and Word document generation.
- `data_model.py` - empty response template and worked example data for tests.
- `tests/` - basic automated tests.
- `requirements.txt` - Python packages needed to run and test the app.

The structure is intentionally modular so later study modules can be added for PK, biodistribution, tumour efficacy, RNA/LNP, or procedure development studies without putting all logic into `app.py`.

## Install Python

If Python is not already installed, install Python 3.10 or newer from:

https://www.python.org/downloads/

On Windows, tick **Add Python to PATH** during installation if the installer offers that option.

## Run locally on Windows PowerShell

Open PowerShell, then move into the repository folder:

```powershell
cd path\to\animal-ethics-study-design-assistant
```

Create a project-specific Python environment:

```powershell
py -m venv .venv
```

Activate it:

```powershell
.\.venv\Scripts\Activate.ps1
```

If PowerShell blocks activation, run this once in the same terminal and then activate again:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\.venv\Scripts\Activate.ps1
```

Install the dependencies into that environment:

```powershell
py -m pip install --upgrade pip
pip install -r requirements.txt
```

Start the app:

```powershell
streamlit run app.py
```

Streamlit should open the app in your browser. If it does not, open:

```text
http://localhost:8501
```

## Run locally on macOS or Linux

```bash
cd path/to/animal-ethics-study-design-assistant
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements.txt
streamlit run app.py
```

## Run tests

After installing dependencies:

```bash
pytest -q
```

The tests cover selected rule triggers and Word report generation from an example response set.

## How questionnaire logic is structured

The questionnaire is split into stages in `config.py`. The visual rendering and conditional fields live in `questionnaire.py`. The decision rules live in `rules.py`, and the report content is assembled in `report_generator.py`.

To add or modify questions:

1. Add options or stage labels in `config.py`.
2. Add the input field in the appropriate `render_stage_*` function in `questionnaire.py`.
3. Add any missing-information checks or warnings in `rules.py`.
4. Add the field to summaries or report tables in `report_generator.py` if it should appear in the final report.
5. Add or update tests in `tests/`.

## Deployment as a shareable link

A simple early deployment path is Streamlit Community Cloud:

1. Push this repository to GitHub.
2. Go to https://share.streamlit.io/.
3. Connect your GitHub account.
4. Select this repository.
5. Use `app.py` as the main file.
6. Deploy.

For institutional use, discuss hosting with your organisation's IT, research governance, and information-security teams. Even though Version 1 does not intentionally store submitted study data, deployed web applications can still create logs or metadata depending on the hosting environment.

## Privacy limitations

Version 1 is session-only and does not intentionally save questionnaire responses. It also does not send responses to external AI or API services. However, users should avoid entering unnecessary personal information, confidential project identifiers, unpublished proprietary details, or sensitive data until the intended hosting environment has been reviewed.

## Recommended next steps before real institutional use

- Review wording with the Animal Ethics Committee office, veterinarian, and relevant investigators.
- Confirm institution-specific humane endpoint and monitoring language.
- Add institutional templates or standard operating procedure references where appropriate.
- Add user acceptance testing with animal research assistants and project leads.
- Add versioning, audit review, and controlled deployment if the tool becomes part of a formal workflow.
