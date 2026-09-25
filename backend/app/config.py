import os
from pathlib import Path
from dotenv import load_dotenv

# Load from backend/.env or root .env
env_path = Path(__file__).resolve().parent.parent / ".env"
load_dotenv(dotenv_path=env_path)

ELEVENLABS_API_KEY = os.getenv("ELEVENLABS_API_KEY", "")

# Groq LLM Fallback Configuration
GROQ_API_KEY = os.getenv("GROQ_API_KEY", "")
GROQ_BASE_URL = os.getenv("GROQ_BASE_URL", "https://api.groq.com/openai/v1")
GROQ_MODEL = os.getenv("GROQ_MODEL", "llama-3.3-70b-versatile")
GROQ_FALLBACK_MODEL = os.getenv("GROQ_FALLBACK_MODEL", "openai/gpt-oss-120b")

# SLM Local Ollama Configuration
OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
SLM_MODEL = os.getenv("SLM_MODEL", "phi3.5:3.8b-mini-instruct-q4_K_M")

# Confidence Gate & Orchestrator Policy
SLM_CONFIDENCE_THRESHOLD = float(os.getenv("SLM_CONFIDENCE_THRESHOLD", "0.75"))
SLM_MAX_RETRIES = int(os.getenv("SLM_MAX_RETRIES", "2"))

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite+aiosqlite:///./equal_plus.db")
