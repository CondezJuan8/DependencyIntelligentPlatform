INSERT INTO assistant_model_providers(code, name)
VALUES ('openai', 'OpenAI')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name;
