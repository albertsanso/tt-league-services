# Summary

Improve inference of club names consolidation.

# Description

Improve the inference of club names consolidation by implementing a more robust algorithm that can handle variations in club names, including abbreviations, misspellings, and different naming conventions. The goal is to ensure that all references to the same club are consolidated under a single canonical name, while preserving the integrity of the data.

# Hard rules

- Process examples labelled with **PENDING** and ensure that the output is consistent with the expected results.
- Skip and ignore examples labelled with **SKIP** or **DONE**.
- When the example is processed change the label from **PENDING** to **DONE**.

# Examples

** Example 1 - DONE
Input: [
"CTT SANT QUIRZE DEL VALLÈS - Sen A",
"CTT ST QUIRZE DEL VALLÈS - Sen C",
"CTT SANT QUIRZE DEL VALLÈS - Vet A",
"CTT SANT QUIRZE DEL VALLÈS - Sen B",
"CTT ST QUIRZE DEL VALLÈS - Vet D A",
"CTT ST QUIRZE DEL VALLÈS - Vet C",
"CTT ST QUIRZE DEL VALLÈS - Vet B",
"CTT ST QUIRZE DEL VALLÈS - Vet A",
"CTT ST QUIRZE DEL VALLÈS - Sen B",
"CTT ST QUIRZE DEL VALLÈS - Vet E B",
"CTT SANT QUIRZE DEL VALLÈS - Vet B",
"CTT SANT QUIRZE DEL VALLÈS - Vet C",
"CTT ST QUIRZE DEL VALLÈS - Sen D"
]
Output: [
"CTT SANT QUIRZE DEL VALLÈS"
]

** Example 2 - DONE
Input: [
"MANUFACTURAS DEPORTIVAS",
"MANUFACTURAS DEPORTIVAS",
"MANUFACTURAS DEPORTIVAS 'A'",
"MANUFACTURAS DEPORTIVAS 'B'"
]
Output: [
"MANUFACTURAS DEPORTIVAS"
]

** Example 3 - DONE
Input: [
"TENNIS TAULA CASSA",
"TENNIS TAULA CASSA",
"TENNIS TAULA CASSÀ"
]
Output: [
"TENNIS TAULA CASSA"
]
Comments: Normalize the club name by removing accents and ensuring consistent spelling.

** Example 4 - DONE
Input: [
"OBERENA 'A'",
"OBERENA "A""
]
Output: [
"OBERENA"
]
Comments: Remove quotes and ensure consistent naming for the club.

** Example 5 - DONE
Input: [
"CLUB TENNIS TAULA TRAMUNTANA FIGUERES",
"CLUB TENNIS TAULA TRAMUNTANA FIGUERES 'A'",
"CLUB TENNIS TAULA TRAMUNTANA FIGUERES 'B'"
]
Output: [
"CLUB TENNIS TAULA TRAMUNTANA FIGUERES"
]
Comments: Normalize the club name by removing quotes and ensuring consistent naming for the club.

** Example 6 - DONE
Input: [
"CLUB TENNIS TAULA BARCELONA",
"CLUB TENNIS TAULA TRAMUNTANA FIGUERES",
"CLUB TENNIS TAULA TORELLÓ",
"CLUB TENNIS TAULA LA BISBAL",
"CLUB TENNIS TAULA TRAMUNTANA FIGUERES 'A'",
"CLUB TENNIS TAULA TRAMUNTANA FIGUERES 'B'",
"CLUB TENNIS TAULA ALTEA",
"CLUB TENNIS TAULA SANTISIMO SALVADOR",
"CLUB TENNIS TAULA OLESA"
]
Those are all different clubs, so the output should be the same as the input, with no consolidation.
But the consolidation is currently applied and resolves to a single club name, which is incorrect. The expected output should be the same as the input list, with no consolidation applied.

The wrong unique club name is:
Output: [
"CLUB TENNIS TAULA ALTEA"
]

The correct output should be:

Club "CLUB TENNIS TAULA BARCELONA", for Team "CLUB TENNIS TAULA BARCELONA"
Club "CLUB TENNIS TAULA TRAMUNTANA FIGUERES", for Teams "CLUB TENNIS TAULA TRAMUNTANA FIGUERES", "CLUB TENNIS TAULA TRAMUNTANA FIGUERES 'A'", "CLUB TENNIS TAULA TRAMUNTANA FIGUERES 'B'"
Club "CLUB TENNIS TAULA TORELLÓ", for Team "CLUB TENNIS TAULA TORELLÓ"
Club "CLUB TENNIS TAULA LA BISBAL", for Team "CLUB TENNIS TAULA LA BISBAL"
Club "CLUB TENNIS TAULA ALTEA", for Team "CLUB TENNIS TAULA ALTEA"
Club "CLUB TENNIS TAULA SANTISIMO SALVADOR", for Team "CLUB TENNIS TAULA SANTISIMO SALVADOR"
Club "CLUB TENNIS TAULA OLESA", for Team "CLUB TENNIS TAULA OLESA"

And another example of a wrong consolidation is:
Input: [
"CLUB TENIS DE MESA SALUD Y DEPORTE",
"CLUB TENIS DE MESA TABOR AÑAVINGO",
"CLUB TENIS DE MESA COSLADA",
"CLUB TENIS DE MESA VILLA DE VALDEMORO",
"CLUB TENIS DE MESA MOS Dismac",
"CLUB TENIS DE MESA MAZDA JEREZ",
"CLUB TENIS DE MESA TECNIK '87",
"CLUB TENIS DE MESA VIGO",
"CLUB TENIS DE MESA VICAR",
"CLUB TENIS DE MESA ALCAZAR",
"CLUB TENIS DE MESA BASAURI"
]
The wrong unique club name is:
Output: [
"CLUB TENIS DE MESA ALCAZAR"
]
The correct output should be:
Club "CLUB TENIS DE MESA SALUD Y DEPORTE", for Team "CLUB TENIS DE MESA SALUD Y DEPORTE"
Club "CLUB TENIS DE MESA TABOR AÑAVINGO", for Team "CLUB TENIS DE MESA TABOR AÑAVINGO"
Club "CLUB TENIS DE MESA COSLADA", for Team "CLUB TENIS DE MESA COSLADA"
Club "CLUB TENIS DE MESA VILLA DE VALDEMORO", for Team "CLUB TENIS DE MESA VILLA DE VALDEMORO"
Club "CLUB TENIS DE MESA MOS Dismac", for Team "CLUB TENIS DE MESA MOS Dismac"
Club "CLUB TENIS DE MESA MAZDA JEREZ", for Team "CLUB TENIS DE MESA MAZDA JEREZ"
Club "CLUB TENIS DE MESA TECNIK '87", for Team "CLUB TENIS DE MESA TECNIK '87"
Club "CLUB TENIS DE MESA VIGO", for Team "CLUB TENIS DE MESA VIGO"
Club "CLUB TENIS DE MESA VICAR", for Team "CLUB TENIS DE MESA VICAR"
Club "CLUB TENIS DE MESA ALCAZAR", for Team "CLUB TENIS DE MESA ALCAZAR"
Club "CLUB TENIS DE MESA BASAURI", for Team "CLUB TENIS DE MESA BASAURI"
