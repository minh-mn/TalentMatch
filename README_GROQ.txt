TALENTMATCH - GROQ AI

1. Open TalentMatch-dev in IntelliJ IDEA, JDK 21, Maven.
2. Run > Edit Configurations > TalentMatchApplication > Environment variables:
   GROQ_API_KEY=<your own key>
   GROQ_MODEL=openai/gpt-oss-20b
   Gemini is optional; remove old invalid GEMINI_API_KEY if not needed.
3. Stop old process on port 8080. Run mvn clean test, then run TalentMatchApplication.
4. Open localhost:8080, log in, Ctrl+F5, open chatbot, select Groq AI and tick consent.
5. Test: "Viet mot doan gioi thieu doanh nghiep 100 tu"; expected prefix Groq AI.
   F12 > Network > assistant/ask > Response: mode GROQ means external API succeeded.
   LOCAL means internal answer or fallback; error messages identify Groq HTTP status.
6. Ask "Du an cua toi" with Vietnamese accents or without; expected LOCAL, scoped database data.
7. Follow-up general questions use up to 8 recent Groq messages in browser memory; changing provider, clearing chat or reloading clears context. No database migration required.
8. Never publish API keys or enter personal/financial data in external AI chat.

Validation: JavaScript syntax checked; Maven and live API calls not tested in this environment.
