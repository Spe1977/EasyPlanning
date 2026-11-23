# Security Policy

## Modello di Sicurezza di EasyPlanning

EasyPlanning è progettata con approccio **privacy-first** e **offline-first**:

- **Nessun server centrale**: tutti i turni, gli straordinari e le indennità risiedono localmente sul dispositivo dell'utente.
- **Backup cifrati end-to-end (client-side)**: sia i file locali `.epbak` sia i backup opzionali su Google Drive sono cifrati prima della scrittura tramite **AES-256-GCM** con chiave derivata via **PBKDF2-HMAC-SHA256** e salt/IV casuali.
- **Permessi minimi**: l'eventuale integrazione con Google Drive utilizza esclusivamente lo spazio isolato `appDataFolder`, senza accesso agli altri file dell'utente.
- **Protezione credenziali locali**: PIN e verificatori di sicurezza sono cifrati con **Android Keystore** e i buffer `CharArray` in memoria vengono azzerati al termine delle operazioni crittografiche.
- **Protezione UI**: opzione `FLAG_SECURE` per impedire screenshot e anteprime nel selettore app recenti, oltre al blocco app con PIN e autenticazione biometrica.

## Segnalazione di Vulnerabilità

Se individui una vulnerabilità di sicurezza:

1. **Non aprire una Issue pubblica** con dettagli sfruttabili.
2. Contatta direttamente il maintainer tramite il profilo GitHub [@Spe1977](https://github.com/Spe1977) o tramite **GitHub Private Vulnerability Reporting** (se abilitato nella scheda *Security* del repository).
3. Includi una descrizione chiara del problema, i passaggi per riprodurlo e l'eventuale impatto stimato.

Riceverai riscontro nel più breve tempo possibile per coordinare analisi e correzione.
