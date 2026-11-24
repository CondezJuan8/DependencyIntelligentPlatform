# Asistente de IA y controles de seguridad

## Alcance actual

La generación usa OpenAI Responses API. Ollama continúa siendo el proveedor local de embeddings y pgvector conserva los vectores y la recuperación filtrada por organización/repositorios. La clave `OPENAI_API_KEY` se configura en `.env`, se entrega solo al backend y no se incluye en el frontend ni en Git.

El asistente es de solo lectura. Recibe la pregunta y hasta seis fragmentos recuperados que el usuario ya puede leer. Produce un diagnóstico, citas y un prompt propuesto para revisión humana. No ofrece herramientas, function calling, acceso a shell, endpoints de escritura de repositorio ni ejecución de cambios. El prompt no es una autorización para modificar código.

## Controles aplicados

- **Autorización antes de recuperar:** el backend verifica `assistant.query` y filtra repositorios por `document.read`; la recuperación vectorial vuelve a acotar resultados por organización/repositorio.
- **Documentos como datos no confiables:** los fragmentos se envían como datos estructurados, separados de las instrucciones del asistente. Las instrucciones incrustadas en documentos no deben cambiar el comportamiento del sistema.
- **Sin capacidad de acción:** la solicitud a OpenAI no declara herramientas ni funciones. Aun si un documento intenta instruir al modelo, no existe una herramienta para escribir o ejecutar.
- **Salida acotada:** Structured Outputs exige JSON con diagnóstico y prompt de reparación; el backend valida campos requeridos, tamaño y que cada etiqueta de cita corresponda a una fuente recuperada. El backend crea las citas visibles a partir de la evidencia, no de enlaces suministrados por el modelo.
- **Revisión humana:** la interfaz etiqueta el prompt como propuesta y solo permite copiarlo. No hay botón de aplicar ni apertura automática de PR.
- **Fallas cerradas:** sin evidencia el caso de uso se abstiene antes de invocar OpenAI. Clave ausente, salida incompleta, rechazo o fallo del proveedor no producen una propuesta parcial.
- **Secreto:** la clave permanece en `.env` local/secret store del despliegue; nunca se expone al navegador. El cliente envía solo la pregunta; el backend recupera y transmite a OpenAI los fragmentos autorizados necesarios para generar la respuesta.

## Límites y siguiente endurecimiento

Las defensas contra prompt injection no dependen solo de un prompt: la barrera principal es que el modelo carece de herramientas y el código aplica autorización/validación fuera del modelo. Aun así, un LLM puede redactar una explicación incorrecta o una propuesta insegura; las citas ayudan a revisar evidencia, no prueban que la conclusión sea correcta. Antes de permitir cambios futuros se debe diseñar un flujo distinto con sandbox aislado, diff revisable, pruebas, límites de recursos, validación de permisos y aprobación explícita por persona.

Las preguntas y fragmentos recuperados salen del equipo hacia OpenAI para generar respuestas. Configura la API key y los controles de datos/retención de la cuenta OpenAI según los requisitos de privacidad de la organización. La API key no se envía por chat.

El diseño sigue las recomendaciones de [OWASP LLM Prompt Injection Prevention](https://cheatsheetseries.owasp.org/cheatsheets/LLM_Prompt_Injection_Prevention_Cheat_Sheet.html) y [OWASP RAG Security](https://cheatsheetseries.owasp.org/cheatsheets/RAG_Security_Cheat_Sheet.html), además de [OpenAI Responses API](https://developers.openai.com/api/reference/resources/responses/methods/create) y [Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses).
