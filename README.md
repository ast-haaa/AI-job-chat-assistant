# HireMate AI — Autonomous AI Job Search & Application Agent

**HireMate AI** is an intelligent, conversational career assistant designed for the **SDE Intern – AI Agents & Automation** assessment project for **Hidani Tech**. 

It provides an end-to-end workflow: from parsing PDF/DOCX resumes, to evaluating real-world listings using transparent weighted matching, to interactive conversational filtering, and tracking local job applications through a multi-turn autonomous agent architecture.

---

## Tech Stack & Architecture Highlights

- **Backend**: Java 21, Spring Boot 3.2.5, Apache Maven
- **Resume Extraction**: Apache PDFBox 3.0.2 (PDF text extraction), Apache POI 5.2.5 (DOCX extraction)
- **Data Persistence**: Thread-safe JSON file storage (`data/jobs.json`, `data/applications.json`)
- **Frontend**: Clean, responsive single-page chat interface written in HTML5, CSS3, and Vanilla JavaScript (No React, Node.js, or external JS build tools required)
- **Architecture**: Modular Tool-based Agent pattern (`OrchestratorAgent`) with session-isolated conversation context (`SessionConversationContext`).

---

## Prerequisites

Before running the application, ensure the following are installed and configured on your system:

- **Java Development Kit (JDK)**: **Java 21** or higher (`java -version`)
- **Apache Maven**: **3.9+** (`mvn -version`)
- **Web Browser**: Modern browser (Chrome, Edge, Firefox, Safari)

---

## Quick Start & Running Locally

### 1. Build and Run the Spring Boot Application

Open a terminal in the root project directory:

```bash
# Clean build and compile
mvn clean package -DskipTests

# Run the Spring Boot application
mvn spring-boot:run
```

Once started, the application serves both backend APIs and the frontend on port **8080**:
- **Chat Web Interface**: [http://localhost:8080](http://localhost:8080)
- **Health Check Endpoint**: [http://localhost:8080/api/health](http://localhost:8080/api/health)
- **Job Listings API**: [http://localhost:8080/api/jobs](http://localhost:8080/api/jobs)

---

## Step-by-Step Feature Breakdown

### 1. Resume Upload & Parsing (Step 2)
- **Endpoint**: `POST /api/resume/parse` (accepts `multipart/form-data`)
- **Supported Formats**: `.pdf` (via Apache PDFBox) and `.docx` (via Apache POI).
- **Validation**: Enforces 5 MB upload limits, rejects empty files, detects encrypted or scanned documents, and handles corrupted files without crashing the server.
- **Extracted Fields**:
  - Full Name
  - Email & Phone Number
  - Target Designation / Role
  - Technical Skills (normalized against an industry dictionary)
  - Work Experience & Internships
  - Education & Degrees
  - Academic & Personal Projects
- **Privacy & Session Isolation**: Parsed profiles are stored strictly within the user's `HttpSession` state. Different browser sessions never share candidate data.

### 2. Real Job Dataset Collection (Step 3)
- **Storage**: `data/jobs.json`
- **Permitted Public APIs**:
  1. **Arbeitnow Public Job Board API**: Free official public job board API (`https://www.arbeitnow.com/api/job-board-api`) returning real tech positions with genuine URLs.
  2. **Remotive Public Tech API**: Official developer job API (`https://remotive.com/api/remote-jobs`).
- **Ingestion Characteristics**:
  - Fully resumable: verifies against existing job IDs and canonical source links before appending.
  - Strips HTML formatting, detects experience level requirements, and catalogs required skill tags.
  - Does **not** fabricate fake jobs or companies.
- **Run the Collector Separately**:
  ```bash
  # Execute the standalone ingestion utility (Target: 2,000 real jobs)
  mvn compile exec:java -Dexec.mainClass="com.hidani.hiremate.ingestion.JobCollector"
  ```
- **Job REST Endpoints**:
  - `GET /api/jobs?page=0&size=10` — Paginated job listings
  - `GET /api/jobs/{jobId}` — Retrieve specific job details
  - `GET /api/jobs/count` — Total verified listings loaded

### 3. Transparent Weighted Job Matching (Step 4)
- **Algorithm**:
  - **Skill Overlap (50% weight)**: Normalized alias matching (e.g., `ReactJS` $\rightarrow$ `React`, `SpringBoot` $\rightarrow$ `Spring Boot`, `K8s` $\rightarrow$ `Kubernetes`).
  - **Title Similarity (30% weight)**: Token overlap between candidate target role and job title.
  - **Experience Alignment (10% weight)**: Compares detected years of experience vs required years. Identifies explicit mismatches (e.g., senior roles requiring 5+ years matched against an entry-level resume).
  - **Remote / Format Fit (10% weight)**: Remote availability alignment.
- **Conversational Filters**:
  - Remote only: *"Show only remote jobs"*
  - Specific skills: *"Show jobs requiring React and TypeScript"*
  - Employment type: *"Find Java internships"*
  - Match threshold: *"Show jobs with at least 70% match"*
  - Location: *"Find roles in Bengaluru"*
  - Reset: *"Clear filters"*

### 4. Autonomous Agent Architecture & Dataflow

HireMate employs a lightweight, decoupled agent architecture without heavyweight external frameworks:

```mermaid
flowchart TD
    User([User / Browser])
    Frontend["HTML5 / CSS / Vanilla JS Chat UI"]
    
    subgraph SpringBoot ["Spring Boot Application Server (Port 8080)"]
        Controllers["REST Controllers\n- ChatController\n- ResumeController\n- JobController\n- ApplicationController"]
        Orchestrator["OrchestratorAgent\n(Central Coordination Engine)"]
        NLU["IntentClassifier\n(Rule-based NLU & Filter Extraction)"]
        Context["SessionConversationContext\n(HttpSession-Scoped State)"]
        
        subgraph Tools ["Agent Tools"]
            HelpTool["GeneralHelpTool"]
            ResumeTool["ResumeParserTool"]
            MatchTool["JobMatchingTool"]
            AppTool["ApplicationTool"]
            StatusTool["ApplicationStatusTool"]
        end
        
        subgraph Services ["Core Business Services"]
            ResumeSvc["ResumeParserService\n(Apache PDFBox / POI)"]
            MatchingSvc["JobMatchingService\n(Weighted Scoring & Filters)"]
            JobDataSvc["JobDataService\n(Validation & Deduplication)"]
            AppSvc["ApplicationService\n(Thread-Safe Tracking)"]
        end
    end
    
    subgraph Storage ["JSON Persistence Storage"]
        JobsFile[("data/jobs.json\n(2,000 Real Jobs)")]
        AppsFile[("data/applications.json\n(Applied Records)")]
    end
    
    User <-->|HTTP / JSON / Multipart| Frontend
    Frontend <-->|REST Endpoints| Controllers
    Controllers <--> Orchestrator
    Orchestrator <--> NLU
    Orchestrator <--> Context
    Orchestrator --> Tools
    
    ResumeTool --> ResumeSvc
    MatchTool --> MatchingSvc
    MatchingSvc --> JobDataSvc
    JobDataSvc <--> JobsFile
    AppTool --> AppSvc
    StatusTool --> AppSvc
    AppSvc <--> AppsFile
```

#### End-to-End Dataflow:
1. **Resume Ingestion**: The user uploads a PDF or DOCX file via `POST /api/resume/parse`. `ResumeParserService` extracts raw text via Apache PDFBox/POI, extracts candidate fields (name, email, skills, experience, education, projects) using regex and normalized dictionaries, and saves the profile in `SessionConversationContext`.
2. **Conversational Intent Classification**: When the user posts a message to `POST /api/chat`, `IntentClassifier` analyzes user utterances, detects intents (greetings, job queries, filter refinements, application requests, status checks), extracts structured filter parameters, and delegates to the corresponding tool.
3. **Job Matching & Filtering**: `JobMatchingTool` pulls the candidate's active profile and filters from `SessionConversationContext`, queries `JobDataService` (which has loaded and deduplicated 2,000 verified listings from `data/jobs.json`), computes transparent weighted scores, and saves the 1-based indexed results back into the session context.
4. **Interactive Application & Confirmation**: When the user says *"Apply to job 2"*, `ApplicationTool` resolves the 2nd listing from the session context, verifies the company/title, and creates a pending confirmation. Once confirmed (*"Yes, apply"*), `ApplicationService` records the application thread-safely in `data/applications.json`.
5. **Session Isolation**: All user states (resumes, recommendation lists, active filters, pending confirmations) are scoped strictly per browser session using `HttpSession`, ensuring multi-user privacy without requiring accounts or logins.

---

### 5. Application Tracking & Confirmation
- **Storage**: Thread-safely stored in `data/applications.json` using `ReentrantLock`.
- **Confirmation Safety**: When a user asks to apply (e.g., *"Apply to job 1"*), the agent identifies the role and employer, prompting:
  > *"You selected **[Job Title]** at **[Company]**. Would you like me to record this application? (Reply 'Yes, apply' to confirm or 'Cancel' to decline)."*
- **Duplicate Prevention**: Prevents duplicate applications to the same job within the same user session.
- **Honest Tracking Disclaimer**: Applications are tracked locally within HireMate AI to assist candidate organization, with direct links provided to original employer career portals.

---

### 6. APIs Used

#### Internal REST Endpoints:
- `GET /api/health` — System status and version response
- `POST /api/chat` — Conversational interface accepting JSON or Multipart (with attachments)
- `POST /api/chat/reset` — Resets session context for a fresh chat
- `POST /api/resume/parse` — Uploads and parses PDF/DOCX resumes
- `GET /api/resume/current` — Retrieves active session profile
- `GET /api/jobs` — Paginated job listings (`?page=0&size=10`)
- `GET /api/jobs/{jobId}` — Retrieves a single job by ID
- `GET /api/jobs/count` — Returns total verified listings loaded
- `POST /api/jobs/match` — Matches jobs against session profile and filters
- `POST /api/applications` — Records a new job application
- `GET /api/applications` — Lists all applications in current session
- `GET /api/applications/{applicationId}` — Status of a specific application

#### External Public APIs:
- **Arbeitnow Public Job API**: `https://www.arbeitnow.com/api/job-board-api` (Official free job board API used to gather 2,000 real software engineering listings with authentic source links).
- **Remotive Public Tech API**: `https://remotive.com/api/remote-jobs` (Integrated as secondary permitted public tech job source).

---

### 7. Deployment Guide (Live Deployed Link)

To get a **live deployed link** as requested in the submission guidelines, you can deploy using any free or low-cost cloud provider in minutes:

#### Option A: Deploy to Render (Recommended - Free)
1. Push your repository to your GitHub account:
   ```bash
   git init
   git add .
   git commit -m "Initial commit of HireMate AI"
   git remote add origin https://github.com/YOUR_USERNAME/hiremate-ai.git
   git push -u origin main
   ```
2. Log in to [render.com](https://render.com) and click **New + > Web Service**.
3. Connect your GitHub repository.
4. Set:
   - **Environment**: `Docker`
   - **Plan**: `Free`
5. Click **Create Web Service**. Render builds the provided `Dockerfile` and gives you a live HTTPS link (e.g., `https://hiremate-ai.onrender.com`).

#### Option B: Deploy to Railway (Fastest)
1. Install Railway CLI or connect via [railway.app](https://railway.app).
2. Choose **Deploy from GitHub repo**.
3. Railway automatically detects Java 21 / Docker and starts the app on port 8080.
4. Generate a public domain in **Settings > Networking**.

#### Option C: Local Tunnel for Live Demo
If you need an immediate live link for testing:
```bash
# Using ngrok or localtunnel
npx localtunnel --port 8080
```
This generates a live public URL forwarding to your local Spring Boot instance.

---

## Example Conversations

| User Prompt | Agent Action |
|---|---|
| *"Hi, what can you do?"* | Explains resume upload, job matching, and application tracking capabilities. |
| *(Uploads resume.pdf)* | Parses resume, extracts skills & experience, displays profile card in chat. |
| *"Find jobs matching my resume"* | Runs weighted matching algorithm, returns top job cards with match % and explanations. |
| *"Show only remote jobs"* | Refines current search preserving resume context, showing only remote positions. |
| *"Apply to job 2"* | Resolves second recommendation, asks user for confirmation before recording. |
| *"Yes, apply"* | Validates job, checks duplicates, saves record to `data/applications.json`, confirms status. |
| *"Show my applied jobs"* | Displays list of all applications submitted in this session. |

---

## Running the Automated Test Suite

The test suite covers:
- Resume parsing (PDF, DOCX, empty files, corrupted docs, missing fields)
- Job loading, deduplication, invalid IDs, pagination
- Matching algorithms, skill normalization, experience mismatch detection
- Intent classification, conversation context, 1-based job indexing
- Application concurrency, duplicate prevention, session isolation

```bash
mvn test
```

---

## Project Structure

```
├── pom.xml
├── README.md
├── .gitignore
├── data/
│   ├── jobs.json             # Real job listings dataset
│   └── applications.json     # Saved application records
└── src/
    ├── main/
    │   ├── java/com/hidani/hiremate/
    │   │   ├── HireMateApplication.java
    │   │   ├── agent/
    │   │   │   ├── Intent.java
    │   │   │   ├── IntentClassifier.java
    │   │   │   ├── OrchestratorAgent.java
    │   │   │   └── tool/
    │   │   │       ├── AgentTool.java
    │   │   │       ├── ApplicationStatusTool.java
    │   │   │       ├── ApplicationTool.java
    │   │   │       ├── GeneralHelpTool.java
    │   │   │       ├── JobMatchingTool.java
    │   │   │       ├── ResumeParserTool.java
    │   │   │       ├── ToolContext.java
    │   │   │       └── ToolResult.java
    │   │   ├── controller/
    │   │   │   ├── ApplicationController.java
    │   │   │   ├── ChatController.java
    │   │   │   ├── HealthController.java
    │   │   │   ├── JobController.java
    │   │   │   └── ResumeController.java
    │   │   ├── ingestion/
    │   │   │   └── JobCollector.java
    │   │   ├── model/
    │   │   │   ├── Application.java
    │   │   │   ├── ApplicationRequest.java
    │   │   │   ├── ChatRequest.java
    │   │   │   ├── ChatResponse.java
    │   │   │   ├── HealthResponse.java
    │   │   │   ├── Job.java
    │   │   │   ├── JobMatchFilter.java
    │   │   │   ├── JobMatchResult.java
    │   │   │   ├── JobPageResponse.java
    │   │   │   ├── ResumeProfile.java
    │   │   │   └── SessionConversationContext.java
    │   │   └── service/
    │   │       ├── ApplicationService.java
    │   │       ├── JobDataService.java
    │   │       ├── JobMatchingService.java
    │   │       └── ResumeParserService.java
    │   └── resources/
    │       ├── application.properties
    │       └── static/
    │           ├── index.html
    │           ├── css/style.css
    │           └── js/app.js
    └── test/
        └── java/com/hidani/hiremate/
            ├── agent/OrchestratorAgentTest.java
            ├── controller/HealthControllerTest.java
            └── service/
                ├── ApplicationServiceTest.java
                ├── JobDataServiceTest.java
                ├── JobMatchingServiceTest.java
                └── ResumeParserServiceTest.java
```

---

## License & Notes
Developed for the Hidani Tech SDE Intern AI Agents & Automation Assessment. All external job source terms and rate limits are respected during ingestion.

