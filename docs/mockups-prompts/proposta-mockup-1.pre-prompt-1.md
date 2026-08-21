Aquesta imatge [proposta-mockup-1]:(assets/img/proposta-mockup-1.png) és la captura d'una app web de partits, clubs, jugadors de tenis taula.

Hi ha 6 grans blocs funcionals principals:
- General
- Jugadors
- Clubs
- Resultats
- Analítica
- Administració

# Secció lateral esquerra
Es un menú que recull tal qual a cada opció accés a cada bloc funcional principal.
El menú es pot replegar amb una icona a la part superior

# Secció superior
Mostra informació contextual variada:

- Breadcrumb o fil d'Ariadna que visualitza la ruta de navegació de la pàgina o funcionalitat/sub funcionalitat actuals. Alineat a l'esquerra.
  El Breadcrumb ha de ser automàtic i ha d'indicar la ruta de navegació en format pila, on l'inici son les funcionalitats principals.

- Bloc d'usuari. Alineat a la dreta. Varis elements:
    - Alarmes. Mostra una señal o alarma indicant si hi ha notificacions pendents de visualitzar.
    - Perfil de l'usuari
    - Nom o identificador de l'usuari. Fent click es desplega un menú d'opcions a nivell d'usuari.

# Secció central
Aquesta secció mostra les funcionalitats seleccionades des de el menu lateral

# Funcionalitats principals

Son accedides a través del menú lateral o desde dins d'una funcionalitat

## Funcionalitat principal: General

### Descripcio

Es una pantalla de presentació, la primera funcionalitat que per defecte s'ha de mostrar a l'usuari.

### Seccions

Alineacio vertical

#### Benvinguda
Presentacio del projecte, text i alguna imatge relacionada amb el Tenis Taula

#### Cercador global
Cerca global de jugadors, clubs o partits.
Una caixa de text lliure ocupant el 90% amplada i un botó "Cercar" a la dreta
Al fer click a "Cercar" s'ha de llençar la cerca i carregar els resultats en una altra pantalla que es navega automàticament

#### Acces rapid
En un layout en linea, esquerra, mig i dreta, i haurà 3 subseccions:

##### Cerca Clubs
Navegacio a Pagina de cerca de clubs

##### Cerca de jugadors
Navegacio a Pagina de cerca de jugadors

##### Cerca de partits i resultats
Navegacio a Pagina de cerca de partits i resultats

#### Resum de la comunitat
En un layout en linea, contatge de: Jugadors, clubs, partits, Indicador temporada actual


prepara un prompt per alimentar un LLM/agent de manera que especifiqui la interficie d'usuari definida, tenint en compta la definicio textual i la imatge proveida. Aplica bones practiques de UX i bones practiques agentiques de desenvolupament de UX. El target stack tecnologic seria npm/React/tailwind.

genera el prompt en format markdown, interpretant el disseny proveit a la imatge i fent una proposta basada en aquest disseny, i generant la proposta de manera esquemàtica al markdown o ascii art

genera un markdown hybrid de markdown + ascii art i descarrega el markdown generat

