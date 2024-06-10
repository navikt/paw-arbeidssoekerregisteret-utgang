# Denne appen er flyttet til [paw-arbeidssoekerregisteret-monorepo-intern](https://github.com/navikt/paw-arbeidssoekerregisteret-monorepo-intern)
# paw-arbeidssoekerregisteret-pdl-utgang

## Hvordan fungerer det

Denne applikasjonen lytter på hendelser fra Kafka, og henter folkeregisterpersonstatus fra PDL for å sjekke om det finnes grunnlag for å avslutte perioder. 

Dersom det finnes grunnlag for å avslutte en arbeidssøkers periode, sendes en avsluttet hendelse med årsak til hendelselogg.

Den lytter på følgende topics:
- paw.arbeidssoekerperioder-v1 (for å hente aktive arbeidssøkerperioder)
- paw.arbeidssoker-hendelseslogg-v1 (for å hente hendelse informasjon fra startet hendelser)

Og sender avsluttet hendelse til:
- paw.arbeidssoker-hendelseslogg-v1

```mermaid
sequenceDiagram
    Kafka->>StateStore: Lagrer informasjon fra startet hendelser i HendelseState
    Kafka->>StateStore: HendelseState oppdateres for aktive arbeidssøkerperioder
    StateStore->>Planlagt oppgave: Itererer gjennom aktive perioder i hendelseState en gang i døgnet
    Planlagt oppgave->>PDL: Bolk henting av "forenkletStatus" i PDL 
    PDL->>Avsluttet hendelse: Hvis forenkletStatus ikke har bosattEtterFolkeregisterloven og har en eller flere av <br>[ikkeBosatt, forsvunnet, doedIFolkeregisteret, opphoert]<br> Hvis opplysninger fra hendelse inneholder 'FORHAANDSGODKJENT_AV_ANSATT' og opplysninger tilsvarende forenkletStatus sendes ikke en avsluttet hendelse.
    Avsluttet hendelse->>Hendelselogg: Send avsluttethendelse til hendelselogg
```
