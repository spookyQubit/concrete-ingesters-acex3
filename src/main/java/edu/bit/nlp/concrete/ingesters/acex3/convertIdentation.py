import json
if __name__ == "__main__":
    for split in ["training"]:
        with open("/Users/d22admin/USCGDrive/ISI/EventExtraction/3Datasets/EventsExtraction" +
                  "/ACE/Preprocessed/JMEE_Dataset/English_new/"+split+".json") as file:
            data = json.load(file)

        new_data = []
        num_events = 0
        sent_with_events = 0
        for sent in data:
            new_data.append({"sentence":sent["sentence"], "stanford-colcc": sent["stanford-colcc"],
                             "golden-entity-mentions": sent["golden-entity-mentions"], "words": sent["words"],
                             "pos-tags": sent["pos-tags"], "golden-event-mentions": sent["golden-event-mentions"]})

            if len(sent["golden-event-mentions"])>0:
                num_events += len(sent["golden-event-mentions"])
                sent_with_events += 1

        with open("/Users/d22admin/USCGDrive/ISI/EventExtraction/3Datasets/EventsExtraction" +
                  "/ACE/Preprocessed/JMEE_Dataset/English_new/"+split+"_prettified.json", "w") as outfile:
            json.dump(new_data, outfile, indent=4, sort_keys=True)

        print("Number of events:", num_events)
        print("Number of sentences:", len(data))
        print("Number of sents with events:", sent_with_events)
