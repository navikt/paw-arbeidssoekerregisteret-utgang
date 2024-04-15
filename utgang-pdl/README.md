# paw-arbeidssoekerregisteret-pdl-utgang


## Hvordan fungerer det

Denne applikasjonen lytter på hendelser fra Kafka, og henter data fra PDL for å sjekke om en arbeidssøker har avsluttet sin periode. 

Dersom en arbeidssøker har avsluttet sin periode, sendes en hendelse til hendelselogg.

Den lytter på følgende topics:
- paw.arbeidssoekerperioder-v1 (for å hente aktive arbeidssøkerperioder)
- paw.arbeidssoker-hendelseslogg-v1 (for å hente informasjon om forhåndsgodkjente arbeidssøkerperioder)

Og sender avsluttet hendelse til:
- paw.arbeidssoker-hendelseslogg-v1

```mermaid
sequenceDiagram
    Kafka->>StateStore: Aktive arbeidssøkerperioder lagres
    Kafka->>StateStore: Lagrer opplysninger som er forhåndsgodkjent av ansatt
    StateStore->>Planlagt oppgave: Henter aktive perioder og evt. forhåndsgodkjenning
    Planlagt oppgave->>PDL: Bolk henting av "forenkletStatus" i PDL 
    PDL->>Avsluttet hendelse: Hvis forenkletStatus ikke har bosattEtterFolkeregisterloven og har<br>[ikkeBosatt, forsvunnet, doedIFolkeregisteret, opphoert, dNummer]<br>Hvis forhåndsgodkjent sjekk at ikke avslagsgrunn fra PDL er samme som fra hendelselogg
    Avsluttet hendelse->>Hendelselogg: Send avsluttethendelse til hendelselogg
```
