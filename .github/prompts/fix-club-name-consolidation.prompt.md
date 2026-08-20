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

** Example 6 - PENDING
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