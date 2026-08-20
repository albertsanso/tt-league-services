# Summary

Apply the JSON model definition in file `../../docs/acta-model-definition.json` to doubles matches in the codebase.
This prompt is used to fix doubles parsing issues by ensuring that the JSON model definition is correctly applied to the relevant parts of the code.

# Description

This prompt ensures that doubles matches in the codebase adhere to the JSON model definition specified in `../../docs/acta-model-definition.json`.
Take into account players `nombre` and `licencia` when applying the model definition to doubles matches.

Doubles JSON node example:
```json
  "dobles": {
    "local": [
      {
        "nombre": "SAFINA , KAMILLA",
        "licencia": "40925"
      },
      {
        "nombre": "RAMOS MARTIN, SARA",
        "licencia": "29257"
      }
    ],
    "visitante": [
      {
        "nombre": "MENDEZ ALONSO, MARIA",
        "licencia": "30900"
      },
      {
        "nombre": "HASEK MENDEZ, ESTHER",
        "licencia": "33469"
      }
    ]
  }
```