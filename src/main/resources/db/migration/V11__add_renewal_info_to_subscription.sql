-- Renewal-Info aus Apples S2S-Notifications (JWSRenewalInfoDecodedPayload).
-- Erlaubt dem Frontend zu zeigen, was NACH dem aktuellen Ablaufdatum passiert:
--   auto_renew_status = false  -> Abo läuft aus, danach Free
--   auto_renew_status = true   -> verlängert sich zu auto_renew_product_id's Plan
--                                 (= gleicher Plan oder geplanter Wechsel/Crossgrade)
--   NULL                       -> noch keine Renewal-Info (S2S noch nicht eingetroffen)
--
-- Beide Spalten nullable und additiv: bestehende Zeilen bleiben "unbekannt"
-- (Frontend zeigt dann die neutrale "aktiv bis"-Variante), bis die nächste
-- Apple-Notification die Felder befüllt.
ALTER TABLE app_store_subscription
    ADD COLUMN auto_renew_status BOOLEAN;

ALTER TABLE app_store_subscription
    ADD COLUMN auto_renew_product_id VARCHAR(255);
