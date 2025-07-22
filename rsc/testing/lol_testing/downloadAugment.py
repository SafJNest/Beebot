import json
import requests

# Load the JSON data from a file in the same folder
with open('augments.json', 'r') as file:
    json_data = json.load(file)

# Base URL for downloading images
base_url = "https://raw.communitydragon.org/latest/game/assets/ux/cherry/augments/icons/"

# Loop through each augment in the JSON data
for augment in json_data['augments']:
    augment_id = augment['id']
    icon_url = base_url + augment['iconLarge'].split('/')[-1]  # Extract the filename from the URL
    
    # Define the local file name
    file_name = f"a{augment_id}.png"
    
    # Download the image
    response = requests.get(icon_url)
    
    if response.status_code == 200:
        # Save the image to a file
        with open(file_name, 'wb') as file:
            file.write(response.content)
        print(f"Downloaded and saved: {file_name}")
    else:
        print(f"Failed to download image for augment ID {augment_id} from {icon_url}")
