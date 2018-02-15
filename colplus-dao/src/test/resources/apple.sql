-- test data
INSERT INTO dataset (key, title, created) VALUES (1, 'First dataset', now());
INSERT INTO dataset (key, title, created) VALUES (2, 'Second dataset', now());
ALTER SEQUENCE dataset_key_seq RESTART WITH 1000;

INSERT INTO name (key, id, dataset_key, scientific_name, rank, origin, type) VALUES (1, 'name-1', 1, 'Malus sylvestris', 'species'::rank, 0, 0);
INSERT INTO name (key, id, dataset_key, scientific_name, rank, origin, type) VALUES (2, 'name-2', 1, 'Larus fuscus', 'species'::rank, 0, 0);
ALTER SEQUENCE name_key_seq RESTART WITH 1000;

INSERT INTO taxon (key, id, dataset_key, name_key, status, origin) VALUES (1, 'root-1', 1, 1, 0, 0);
INSERT INTO taxon (key, id, dataset_key, name_key, status, origin) VALUES (2, 'root-2', 1, 2, 0, 0);
ALTER SEQUENCE taxon_key_seq RESTART WITH 1000;

INSERT INTO reference(key, id, dataset_key) VALUES (1, 'ref-1', 1);
INSERT INTO reference(key, id, dataset_key) VALUES (2, 'ref-2', 2);
ALTER SEQUENCE reference_key_seq RESTART WITH 1000;

INSERT INTO taxon_reference(dataset_key, taxon_key, reference_key) VALUES (1, 1, 1);
INSERT INTO taxon_reference(dataset_key, taxon_key, reference_key) VALUES (1, 2, 1);
INSERT INTO taxon_reference(dataset_key, taxon_key, reference_key) VALUES (1, 2, 2);

INSERT INTO name_act(key, dataset_key, type, name_key, reference_key, reference_page) VALUES (1, 1, 0, 1, 1, '712');
ALTER SEQUENCE name_act_key_seq RESTART WITH 1000;

INSERT INTO distribution(key, dataset_key, taxon_key, area, area_standard) VALUES (1, 1, 1, 'Berlin', 6);
INSERT INTO distribution(key, dataset_key, taxon_key, area, area_standard) VALUES (2, 1, 1, 'Leiden', 6);
INSERT INTO distribution(key, dataset_key, taxon_key, area, area_standard) VALUES (3, 1, 2, 'New York', 6);
ALTER SEQUENCE distribution_key_seq RESTART WITH 1000;


INSERT INTO distribution_reference(dataset_key,distribution_key,reference_key) VALUES (1, 1, 1);
INSERT INTO distribution_reference(dataset_key,distribution_key,reference_key) VALUES (1, 1, 2);
INSERT INTO distribution_reference(dataset_key,distribution_key,reference_key) VALUES (1, 2, 2);

INSERT INTO vernacular_name(key,dataset_key,taxon_key,name,language) VALUES (1, 1, 1, 'Apple', 'en');
INSERT INTO vernacular_name(key,dataset_key,taxon_key,name,language) VALUES (2, 1, 1, 'Apfel', 'de');
INSERT INTO vernacular_name(key,dataset_key,taxon_key,name,language) VALUES (3, 1, 1, 'Meeuw', 'nl');
ALTER SEQUENCE vernacular_name_key_seq RESTART WITH 1000;

INSERT INTO vernacular_name_reference(dataset_key,vernacular_name_key,reference_key) VALUES (1, 1, 1);
INSERT INTO vernacular_name_reference(dataset_key,vernacular_name_key,reference_key) VALUES (1, 2, 1);


