INSERT INTO scanner_tools(code, name) VALUES ('npm-lockfile', 'npm package-lock reader') ON CONFLICT (code) DO NOTHING;
