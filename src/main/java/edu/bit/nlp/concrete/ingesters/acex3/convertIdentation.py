import json
if __name__ == "__main__":
    sent_with_events_total = 0
    sent_with_one_events_total = 0
    for split in ["dev", "training", "test"]:
        with open("/Users/d22admin/USCGDrive/ISI/EventExtraction/3Datasets/EventsExtraction" +
                  "/ACE/Preprocessed/JMEE_Dataset/English_new/"+split+".json") as file:
            data = json.load(file)

        new_data = []
        num_events = 0
        sent_with_events = 0
        sent_with_one_events = 0
        for sent in data:
            new_data.append({"sentence": sent["sentence"], "stanford-colcc": sent["stanford-colcc"],
                             "golden-entity-mentions": sent["golden-entity-mentions"], "words": sent["words"],
                             "pos-tags": sent["pos-tags"], "golden-event-mentions": sent["golden-event-mentions"]})

            if len(sent["golden-event-mentions"])>0:
                num_events += len(sent["golden-event-mentions"])
                sent_with_events += 1

                if len(sent["golden-event-mentions"])>1:
                    sent_with_one_events += 1

        with open("/Users/d22admin/USCGDrive/ISI/EventExtraction/3Datasets/EventsExtraction" +
                  "/ACE/Preprocessed/JMEE_Dataset/English_new/"+split+"_prettified.json", "w") as outfile:
            json.dump(new_data, outfile, indent=4, sort_keys=True)

        print("Number of events:", num_events)
        print("Number of sentences:", len(data))
        print("Number of sents with events:", sent_with_events)
        sent_with_one_events_total += sent_with_one_events
        sent_with_events_total += sent_with_events
        print("Number of sents with more than one event:", sent_with_one_events)

print("Total Number of sents with more than one event:", sent_with_one_events_total)
print("Total Number of sents with at least one event:", sent_with_events_total)
