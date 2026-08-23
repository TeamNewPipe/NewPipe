import openai
import json
from forbiddenfruit import curse
import sys
import config
from concurrent.futures import ThreadPoolExecutor
import tempfile
import subprocess
import os
import time

def filter_list(self, func):
    """
    Filter the list based on a given lambda function.

    Parameters:
    func (function): The lambda function used for filtering.

    Returns:
    list: The filtered list.
    """
    return list(filter(func, self))


def map_list(self, func):
    """
    Map the list based on a given lambda function.

    Parameters:
    func (function): The lambda function used for mapping.

    Returns:
    list: The mapped list.
    """
    return list(map(func, self))


def filter_dict(self, func):
    """
    Filter the dictionary based on a given lambda function operating on key-value pairs.

    Parameters:
    func (function): The lambda function used for filtering (takes key and value as input).

    Returns:
    dict: The filtered dictionary.
    """
    return {k: v for k, v in self.items() if func(k, v)}


# Use forbiddenfruit to add the filter_dict method to the built-in dict class
curse(list, "filter", filter_list)
curse(list, "map", map_list)
curse(dict, "filter", filter_dict)


class Translator:
    def __init__(self):
        self.client = openai.OpenAI(api_key=config.api_key, base_url=config.base_url)

    def translate(self, content, language):
        extra_note = ""
        response = self.client.chat.completions.create(
            model="gemini-3.1-pro-preview",
            response_format={"type": "json_object"},
            messages=[
                {
                    "role": "user",
                    "content": f"Translate the values(not including the keys) in the dict to values-{language}. Always preserve new line tokens like \\n. Output result in json format. \n\n{content}" + extra_note,
                }
            ],
            temperature=0,
        )
        raw = response.choices[0].message.content.strip()
        if raw.startswith("```json"):
            raw = raw[len("```json"):]
        elif raw.startswith("```"):
            raw = raw[len("```"):]
        if raw.endswith("```"):
            raw = raw[:-len("```")]
        translated_text = raw.strip()
        print(translated_text)
        return translated_text

    def get_translated_dict(self, content, language):
        return json.loads(self.translate(content, language))


from bs4 import BeautifulSoup


class XMLHandler:
    def __init__(self, file_path=None):
        self.file_path = file_path
        self.soup = None
        if file_path:
            self.load_from_file(file_path)

    def load_from_file(self, file_path):
        with open(file_path, 'r') as file:
            self.soup = BeautifulSoup(file, 'xml')
        self.file_path = file_path

    def add_entry(self, parent_selector, new_tag, attributes=None, text=None):
        parent = self.soup.select_one(parent_selector)
        if parent is None:
            raise Exception(f"The parent with selector {parent_selector} does not exist.")
        new_element = self.soup.new_tag(new_tag)
        if attributes:
            new_element.attrs = attributes
        if text:
            new_element.string = text
        parent.append(new_element)
        parent.append("\n")

    def update_entry(self, selector, new_text=None):
        element = self.soup.select_one(selector)
        if element is None:
            raise Exception(f"The element with selector {selector} does not exist.")
        if new_text is not None:
            element.string = new_text

    def delete_entry(self, selector):
        element = self.soup.select_one(selector)
        if element:
            element.decompose()

    def write_to_file(self, file_path=None):
        if file_path is None:
            if self.file_path:
                file_path = self.file_path
            else:
                raise Exception("No file path specified for writing XML data.")
        with open(file_path, 'w') as file:
            file.write(str(self.soup))

    def load_strings_to_dict(self, tail_length=0):
        strings_dict = {}
        string_tags = self.soup.find_all('string')
        for tag in string_tags:
            name = tag.get('name')
            if name:
                strings_dict[name] = tag.text
        return dict(list(strings_dict.items())[-1 * tail_length:])

    def load_strings_to_dict_by_part(self):
        strings_dict = {}
        string_tags = self.soup.find_all('string')
        result = []
        start = 0
        while start < len(string_tags):
            for tag in string_tags:
                name = tag.get('name')
                if name:
                    strings_dict[name] = tag.text
            result.append(dict(list(strings_dict.items())[start:start + 100]))
            start += 100
        return result

def escape(text):
    result = ''
    i = 0
    while i < len(text):
        if text[i] == '\\' and i + 1 < len(text) and text[i + 1] == "'":
            result += "\\'"
            i += 2
        elif text[i] == '\\' and i + 1 < len(text) and text[i + 1] == '"':
            result += '\\"'
            i += 2
        elif text[i] == '"':
            result += '\\"'
            i += 1
        elif text[i] == "'":
            result += "\\'"
            i += 1
        else:
            result += text[i]
            i += 1
    return result.replace("\n", "\\n")


def get_user_input_from_vim(initial_content=""):
    """
    Open vim editor for user to input text.

    Parameters:
    initial_content (str): Initial content to show in the editor

    Returns:
    str: The content entered by the user, or None if cancelled
    """
    # Create a temporary file
    with tempfile.NamedTemporaryFile(mode='w+', suffix='.txt', delete=False) as tmp_file:
        tmp_file.write(initial_content)
        tmp_file_path = tmp_file.name

    try:
        # Open vim with the temporary file
        editor = os.environ.get('EDITOR', 'vim')
        subprocess.run([editor, tmp_file_path], check=True)

        # Read the content back
        with open(tmp_file_path, 'r') as tmp_file:
            content = tmp_file.read().strip()

        return content.replace("\n", "\\n") if content else None

    except subprocess.CalledProcessError:
        print("Editor was cancelled or failed.")
        return None
    except Exception as e:
        print(f"Error opening editor: {str(e)}")
        return None
    finally:
        # Clean up the temporary file
        try:
            os.unlink(tmp_file_path)
        except:
            pass



def compare_xml_strings_with_values(xml_a_path, xml_b_path):
    """
    Compare two XML files and return entries that are in A but not in B,
    or have different values.

    Parameters:
    xml_a_path (str): Path to the first XML file
    xml_b_path (str): Path to the second XML file

    Returns:
    dict: Dictionary containing entries that are different
    """
    xml_a = XMLHandler(xml_a_path)
    xml_b = XMLHandler(xml_b_path)

    strings_a = xml_a.load_strings_to_dict()
    strings_b = xml_b.load_strings_to_dict()

    diff_entries = {}

    for key, value in strings_a.items():
        if key not in strings_b or strings_b[key] != value:
            diff_entries[key] = value

    return diff_entries



class StringTranslator:

    def __init__(self):
        self.base = XMLHandler('app/src/main/res/values/strings.xml')
        self.targets = [
            XMLHandler('app/src/main/res/values-zh-rCN/strings.xml'),
            XMLHandler('app/src/main/res/values-zh-rTW/strings.xml'),
            XMLHandler('app/src/main/res/values-ja/strings.xml'),
            XMLHandler('app/src/main/res/values-vi/strings.xml'),
            XMLHandler('app/src/main/res/values-fr/strings.xml'),
            XMLHandler('app/src/main/res/values-de/strings.xml'),
            XMLHandler('app/src/main/res/values-it/strings.xml'),
            XMLHandler('app/src/main/res/values-es/strings.xml'),
            XMLHandler('app/src/main/res/values-pl/strings.xml'),
        ]
        self.translator = Translator()

    def translate_latest_updates_to_all(self, length, ignore_update=False):
        updates = self.base.load_strings_to_dict(tail_length=int(length))
        def translate_and_update_target(target):
            result = self.translator.get_translated_dict(updates, target.file_path.split('/')[-2][7:])
            if ignore_update:
                print(result)
                return
            for key, value in result.items():
                try :
                    target.update_entry(f'string[name="{key}"]', escape(value))
                except:
                    target.add_entry('resources', 'string', {'name': key}, escape(value))
            target.write_to_file()
            # Process translations in parallel
        with ThreadPoolExecutor() as executor:
            executor.map(translate_and_update_target, self.targets)

    def translate_everything(self):
        data = self.base.load_strings_to_dict_by_part()
        for target in self.targets:
            for updates in data:
                result = self.translator.get_translated_dict(updates, target.file_path.split('/')[-2][7:])
                for key, value in result.items():
                    target.add_entry('resources', 'string', {'name': key}, escape(value))
            target.write_to_file()

    def translate_item_updates_to_all(self, item_list):
        updates = self.base.load_strings_to_dict().filter(lambda k, v: k in item_list)

        def translate_and_update_target(target):
            result = self.translator.get_translated_dict(updates, target.file_path.split('/')[-2][7:])
            for key, value in result.items():
                try :
                    target.update_entry(f'string[name="{key}"]', escape(value))
                except:
                    target.add_entry('resources', 'string', {'name': key}, escape(value))
            target.write_to_file()

        # Process translations in parallel
        with ThreadPoolExecutor() as executor:
            executor.map(translate_and_update_target, self.targets)

    def _translate_with_retry(self, updates, language, retries=3, retry_delay=2):
        last_error = None
        for attempt in range(retries):
            try:
                return self.translator.get_translated_dict(updates, language)
            except Exception as e:
                last_error = e
                print(f"Translation failed for {language} (attempt {attempt + 1}/{retries}): {str(e)}")
                if attempt < retries - 1:
                    time.sleep(retry_delay * (attempt + 1))
        raise last_error

    def translate_missing_entries_only(self, chunk_size=100, chunk_workers=4):
        base_strings = self.base.load_strings_to_dict()

        def translate_and_add_missing(target):
            existing_strings = target.load_strings_to_dict()
            missing_keys = [k for k in base_strings.keys() if k not in existing_strings]

            if not missing_keys:
                print(f"No missing entries for {target.file_path}")
                return

            language = target.file_path.split('/')[-2][7:]
            chunked_missing_keys = [
                missing_keys[i:i + chunk_size]
                for i in range(0, len(missing_keys), chunk_size)
            ]
            total_chunks = len(chunked_missing_keys)

            def translate_chunk(index_and_keys):
                idx, chunk = index_and_keys
                missing_entries = {k: base_strings[k] for k in chunk}
                result = self._translate_with_retry(missing_entries, language)
                print(
                    f"{target.file_path}: translated chunk "
                    f"{idx + 1}/{total_chunks} ({len(chunk)} entries)"
                )
                return idx, result

            translated_chunks = {}
            with ThreadPoolExecutor(max_workers=chunk_workers) as chunk_executor:
                for idx, result in chunk_executor.map(translate_chunk, enumerate(chunked_missing_keys)):
                    translated_chunks[idx] = result

            for idx in range(total_chunks):
                result = translated_chunks[idx]
                for key, value in result.items():
                    try:
                        target.update_entry(f'string[name="{key}"]', escape(value))
                    except:
                        target.add_entry('resources', 'string', {'name': key}, escape(value))

            target.write_to_file()
            print(f"Added {len(missing_keys)} missing entries to {target.file_path}")

        with ThreadPoolExecutor() as executor:
            executor.map(translate_and_add_missing, self.targets)

    def add_new_entry(self, name, value):
        self.base.add_entry('resources', 'string', {'name': name}, escape(value))
        self.base.write_to_file()
        updates = {name: value}

        def translate_and_update(target):
            result = self.translator.get_translated_dict(updates, target.file_path.split('/')[-2][7:])
            target.add_entry('resources', 'string', {'name': name}, escape(result[name]))
            target.write_to_file()

            # Process translations in parallel
        with ThreadPoolExecutor() as executor:
            executor.map(translate_and_update, self.targets)

    def translate_new_entries(self, item_list):
        updates = self.base.load_strings_to_dict().filter(lambda k, v: k in item_list)
        for target in self.targets:
            result = self.translator.get_translated_dict(updates, target.file_path.split('/')[-2][7:])
            for key, value in result.items():
                target.add_entry('resources', 'string', {'name': key}, escape(value))
            target.write_to_file()

    def delete_entry(self, name):
        self.base.delete_entry(f'string[name="{name}"]')
        self.base.write_to_file()
        for target in self.targets:
            target.delete_entry(f'string[name="{name}"]')
            target.write_to_file()

    def update_translations_from_xml(self, input_xml_path, language):
        """
        Update translations in an existing XML file with entries from an input XML file.

        Parameters:
        input_xml_path (str): Path to the input XML file containing new translations
        language (str): Target language code (e.g., 'zh-rCN', 'fr', 'de')

        Returns:
        bool: True if successful, False otherwise
        """
        try:
            # Load the input XML file
            input_xml = XMLHandler(input_xml_path)
            input_strings = input_xml.load_strings_to_dict()

            # Find the target XML file for the specified language
            target = None
            for xml_handler in self.targets:
                if language in xml_handler.file_path:
                    target = xml_handler
                    break

            if not target:
                print(f"Error: No translation file found for language '{language}'")
                return False

            # Load existing translations
            existing_strings = target.load_strings_to_dict()

            # Find common entries between input and existing translations
            common_keys = set(input_strings.keys()) & set(existing_strings.keys())

            # Update the entries in the target XML
            for key in common_keys:
                target.update_entry(f'string[name="{key}"]', escape(input_strings[key]))

            # Save the updated XML
            target.write_to_file()

            print(f"Successfully updated {len(common_keys)} translations for language '{language}'")
            return True

        except Exception as e:
            print(f"Error updating translations: {str(e)}")
            return False

    def update_with_replace(self, name, new_value):
        """
        Update an existing entry with a new value and translate it to all target languages.

        Parameters:
        name (str): The name of the string entry to update
        new_value (str): The new value to set for the entry. If None, will prompt user via editor.
        """

        # Update the base XML with the new value
        try:
            self.base.update_entry(f'string[name="{name}"]', escape(new_value))
            self.base.write_to_file()
            print(f"Updated base entry '{name}' with new value.")
        except Exception as e:
            raise Exception(f"Failed to update base entry '{name}': {str(e)}")

        # Prepare the update dictionary
        updates = {name: new_value}

        def translate_and_update_target(target):
            try:
                # Get translation for the target language
                result = self.translator.get_translated_dict(updates, target.file_path.split('/')[-2][7:])
                translated_value = result[name]

                # Update or add the entry in the target XML
                try:
                    target.update_entry(f'string[name="{name}"]', escape(translated_value))
                except:
                    target.add_entry('resources', 'string', {'name': name}, escape(translated_value))

                target.write_to_file()
            except Exception as e:
                print(f"Error updating target {target.file_path}: {str(e)}")

        # Process translations in parallel
        with ThreadPoolExecutor() as executor:
            executor.map(translate_and_update_target, self.targets)

        print(f"Successfully updated and translated '{name}' to all target languages.")

    def compare_xml_strings(self, xml_a_path=0, xml_b_path=1):
        """
        Compare two XML files and return entries that are in A but not in B.

        Parameters:
        xml_a_path (str): Path to the first XML file
        xml_b_path (str): Path to the second XML file

        Returns:
        dict: Dictionary containing entries that are in A but not in B
        """
        xml_a = XMLHandler(self.base.file_path)
        xml_b = XMLHandler(self.targets[xml_b_path].file_path)

        strings_a = xml_a.load_strings_to_dict()
        strings_b = xml_b.load_strings_to_dict()

        # Find keys that are in A but not in B
        diff_keys = set(strings_a.keys()) - set(strings_b.keys())

        # Return the entries that are in A but not in B
        return {key: strings_a[key] for key in diff_keys}

def get_value_from_vim():
    # If new_value is not provided, get it from the user via editor
        # Try to get the current value to show as initial content
    try:
        current_strings = self.base.load_strings_to_dict()
        initial_content = current_strings.get(name, "")
    except:
        initial_content = ""

    print(f"Opening editor to input new value for '{name}'...")
    print("Current value will be shown as initial content. Save and exit to confirm.")

    return get_user_input_from_vim(initial_content)



if __name__ == '__main__':
    translator = StringTranslator()
    # translator.update_translations_from_xml('tmp.xml', 'ja')
    # read params
    args = sys.argv
    if args[1] == 'add':
        if args[-1] == '-i':
            translator.add_new_entry(args[2], get_user_input_from_vim())
        else:
            translator.add_new_entry(args[2], args[3])
    elif args[1] == 'delete' or args[1] == 'remove':
        for i in args[2:]:
            translator.delete_entry(i)
    elif args[1] == 'update_multi':
        translator.translate_item_updates_to_all(args[2:])
    elif args[1] == 'add_multi':
        translator.translate_new_entries(args[2:])
    elif args[1] == 'translate':
        translator.translate_everything()
    elif args[1] == 'update_latest':
        translator.translate_latest_updates_to_all(args[2])
    elif args[1] == 'update':
        if len(args) == 3:
            translator.translate_item_updates_to_all(args[2:])
        elif args[-1] == "-i":
            translator.update_with_replace(args[2], get_user_input_from_vim())
        elif len(args) == 4:
            translator.update_with_replace(args[2], args[3])
    elif args[1] == 'sync' or args[1] == 'sync_missing':
        chunk_size = int(args[2]) if len(args) >= 3 else 100
        chunk_workers = int(args[3]) if len(args) >= 4 else 4
        translator.translate_missing_entries_only(
            chunk_size=chunk_size,
            chunk_workers=chunk_workers
        )
    elif args[1] == 'compare':
        if len(args) == 3:
            diff = translator.compare_xml_strings(xml_b_path=int(args[2]))
            with open("compare_temp.txt", "w") as f:
                f.write("Entries in first file but not in second:\n")
                for key, value in diff.items():
                    f.write(f"  {key}: {value}\n")
